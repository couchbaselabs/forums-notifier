package com.couchbase.research.forumsTweets;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

import com.couchbase.client.java.*;
import com.couchbase.client.java.document.json.*;
import com.couchbase.client.java.env.CouchbaseEnvironment;
import com.couchbase.client.java.env.DefaultCouchbaseEnvironment;
import com.couchbase.client.java.query.*;

@SuppressWarnings("unused")
@Path("/query")
public class QueryInterface 
{
	/**
	 * Here is the HTML header we embed for providing table styles for our
	 * results.
	 */

	public static String htmlHeader = 
			"<html><head><style TYPE=\"text/css\"><!--"
					+ ".cbforums {"
					+ "  border: 1px solid #ddd;"
					+ "  font-style: italic;"
					+ "  color: #5286BC;"
					+ "  style: margin-left;"
					+ "} "
					+ ".cbforums > tbody > tr:nth-child(odd) {"
					+ "  background-color: #f5f5f5;"
					+ "} "
					+ ".cbforums > tbody > tr:nth-child(even) {"
					+ "  background-color: #fff;"
					+ "} "
					+ "--></style></head><body>";

	public static String htmlFooter =
			"</body></html>";

	Cluster cbCluster = null;
	Bucket forums = null;
	Bucket stackOverflow = null;
	Bucket forumSubscribers = null;

	/**
	 * Initialize our connection to the cluster.
	 */

	void initCluster()
	{
		System.setProperty("com.couchbase.queryEnabled", "true");

		if (cbCluster == null) {
//			CouchbaseEnvironment env = DefaultCouchbaseEnvironment.builder().
//					bootstrapHttpDirectPort(9000).bootstrapCarrierDirectPort(12000).
//					build();
//			cbCluster = CouchbaseCluster.create(env);

			//cbCluster = CouchbaseCluster.create("localhost");
			cbCluster = CouchbaseCluster.create("research.hq.couchbase.com");
		}

		if (forums == null)
			forums = cbCluster.openBucket("Forums");

		if (forumSubscribers == null)
			forumSubscribers = cbCluster.openBucket("ForumSubscribers");

		if (stackOverflow == null)
			stackOverflow = cbCluster.openBucket("StackOverflow");
	}


	/**
	 * Get all unanswered posts containing one of a comma-delimited list of terms that 
	 * were posted within a certain number of days.
	 * 
	 * @param termlist
	 * @param since
	 * @return
	 */

	@GET
	@Path("/unanswered")
	@Produces(MediaType.TEXT_HTML)
	public String findUnansweredPostsWithTerms(
			@QueryParam("terms") String termlist,
			@QueryParam("since") String since,
			@QueryParam("categories") String categorylist
			) 
	{
		initCluster();

		if (termlist == null && since == null) // don't let them query everything
			since = "7";

		String whereExpr = buildConditionalExpr(termlist,since,categorylist);
		String whereClause = (whereExpr.length() > 0) ? " and " + whereExpr : "";

		String query = "(select min(category) category, min(dateStr) dateStr, "
				+ "min(author) author, min(postHTML) postHTML, min(summary) summary, "
				+ "min(concat(\"http://forums.couchbase.com/t/\",to_string(thread_num))) link "
				+ "from Forums " 
				+ "group by thread_num " 
				+ "having count(*) = 1 " + whereClause				
				+ " order by thread_num desc) "
				+ "union all "
				+ "(select foo.* from (select \"StackOverflow\" category, "
				+ "title summary, postHTML, "
				+ "is_answered, "
				+ "owner.display_name author, link, post, "
				+ "millis_to_str(creation_date*1000) dateStr "
				+ "from StackOverflow) foo where is_answered = false " + whereClause + ")";
		System.out.printf("Got query: %s\n",query);

		QueryResult res = forums.query(Query.simple(query));

		String termNotice = (termlist == null) ? "" : String.format(" containing terms: '%s' ", termlist);
		String categoryNotice = (categorylist == null) ? "" : String.format(" from categories: '%s' ", categorylist);
		String sinceNotice = (since == null) ? "" : String.format(" in the past %s days", since);
		String prefix = String.format("<H2>Unanswered posts%s%s%s</h2>",termNotice,categoryNotice,sinceNotice);
		return(convertResultToHTMLTable(prefix, termlist, res));
	}


	/**
	 * Get all posts containing one of a comma-delimited list of terms that 
	 * were posted within a certain number of days.
	 * 
	 * @param termlist
	 * @param since
	 * @return
	 */

	@GET
	@Path("/")
	@Produces(MediaType.TEXT_HTML)
	public String findPostsWithTerms(
			@QueryParam("terms") String termlist,
			@QueryParam("since") String since,
			@QueryParam("categories") String categorylist
			) 
	{
		System.out.printf("Got termlist: %s and since %s\n", termlist, since);
		initCluster();

		if (termlist == null && since == null) // don't let them query everything
			since = "7";

		String whereExpr = buildConditionalExpr(termlist,since,categorylist);
		String whereClause = (whereExpr.length() > 0) ? " where " + whereExpr : "";

		// make the query		
		String query = "select foo2.* from ((select f1.*, concat(\"http://forums.couchbase.com/t/\",to_string(thread_num)) link "
				+ "from Forums f1 "+ whereClause + " ) "
				+ "union all "
				+ "(select foo.* from (select \"StackOverflow\" category, "
				+ "title summary, post, "
				+ "owner.display_name author, link, postHTML, "
				+ "millis_to_str(creation_date*1000) dateStr "
				+ "from StackOverflow) foo " + whereClause + ")) foo2 order by summary, dateStr desc";
		System.out.printf("Got query: %s\n",query);

		QueryResult res = forums.query(Query.simple(query));

		String termNotice = (termlist == null) ? "" : String.format(" containing terms: '%s' ", termlist);
		String categoryNotice = (categorylist == null) ? "" : String.format(" from categories: '%s' ", categorylist);
		String sinceNotice = (since == null) ? "" : String.format(" in the past %s days", since);
		String prefix = String.format("<H2>Posts%s%s%s</h2>",termNotice,categoryNotice,sinceNotice);
		return(convertResultToHTMLTable(prefix, termlist, res));
	}


	/**
	 * Get all subscribers
	 * 
	 */

	@GET
	@Path("/subscribers/get")
	@Produces(MediaType.APPLICATION_JSON)
	public String findSubscribers() 
	{
		initCluster();
		QueryResult res = forumSubscribers.query(Query.simple("select ForumSubscribers.* from ForumSubscribers"));
		JsonArray result = JsonArray.create();

		for (QueryRow row : res.allRows())
		{
			JsonObject val = row.value();
			result.add(val);
		}

		return result.toString();
	}

	@GET
	@Path("/subscribers/put")
	@Produces(MediaType.TEXT_HTML)
	public String putSubscriber(
			@QueryParam("email") String email,
			@QueryParam("terms") String termlist,
			@QueryParam("since") String since,
			@QueryParam("categories") String categorylist,
			@QueryParam("unanswered") boolean unanswered
			) 
	{
		initCluster();
		if (email == null)
			return("Error, email is null");

		String objectDef = "{\"email\":\"" + email + "\"" + 
				((termlist != null) ? ", \"terms\":\"" + termlist + "\"" : "") +
				((since != null) ? ", \"since\":\"" + since + "\"" : "") +
				((categorylist != null) ? ", \"categories\":\"" + categorylist + "\"" : "") +
				", \"unanswered\":" + unanswered + "}";
		
		String query = String.format("upsert into ForumSubscribers (PRIMARY KEY, VALUE) "
				+ "values (\"%s\", %s)",email,objectDef);
		
		System.out.printf("query: %s\n",query);

		QueryResult res = forumSubscribers.query(Query.simple(query));

		return(res.toString());
	}

	@GET
	@Path("/subscribers/delete")
	@Produces(MediaType.TEXT_HTML)
	public String deleteSubscriber(
			@QueryParam("email") String email
			) 
	{
		System.out.println("Deleting subscriber: " + email);
		if (email == null)
			return("Error, null subscriber email");
		initCluster();
		QueryResult res = forumSubscribers.query(Query.simple(
				String.format("delete from ForumSubscribers use PRIMARY KEYS \"%s\";",
						email)));

		return(res.toString());
	}
	/**
	 * Build a conditional expression based on an option termlist and since number
	 */

	private String buildConditionalExpr(String termlist, String since, String categorylist)
	{
		String expr = "";

		// do we need a where clause?
		if (termlist != null || since != null) 
		{
			if (termlist != null) 
			{
				expr += termlistToExpr(termlist);
			}
			if (since != null) 
			{
				if (expr.length() > 0)
					expr += " and ";

				expr += "millis(dateStr) > (now_millis() - 1000*60*60*24*" + since + ")";
			}
			if (categorylist != null) 
			{
				String categories[] = categorylist.split(",");
				if (categories.length > 0) 
				{
					if (expr.length() > 0)
						expr += " and ";

					expr += "(";
					for (int i = 0; i < categories.length; i++) 
					{
						if (i > 0)
							expr += " or ";
						expr += "category = '" + categories[i] + "' ";			
					}
					expr += ") ";
				}
			}
		}

		return(expr);
	}


	/**
	 * Given a comma-delimited termlist, turn it into a N1QL conditional
	 * expression
	 */

	private String termlistToExpr(String termlist) 
	{
		String expr = " ";

		if (termlist == null)
			return(expr);

		String terms[] = termlist.split(",");
		if (terms.length > 0) 
		{
			expr += "(";
			for (String term : terms) 
			{
				if (expr.length() > 7)
					expr += " or ";
				expr += "regex_contains(lower(post),lower('" + term + "'))";			
			}
			expr += ") ";
		}

		return(expr);
	}


	/**
	 * Given a query result from "select * from Forums where...", convert
	 * the results to an HTML table
	 * 
	 * @param res
	 * @return
	 */

	private String convertResultToHTMLTable(String prefix, String termlist, QueryResult res)
	{
		String terms[] = termlist != null ? termlist.split(",") : null;

		if (res.parseSuccess()) 
		{
			String result = prefix;
			if (res.allRows().size() > 0)
			{
				int prev_thread_num = 0;
				result += "<table class=\"cbforums\"><thead  class=\"cbforums\">"
						+ "<th  class=\"cbforums\">Date</th>"
						+ "<th class=\"cbforums\">Summary</th><th class=\"cbforums\">Category</th>"
						+ "<th class=\"cbforums\">Author</th><th class=\"cbforums\">Post</th></thead><tbody class=\"cbforums\">";

				for (QueryRow row : res.allRows())
				{
					JsonObject val = row.value();
					String post = val.getString("postHTML");

					//if (post != null)
					//	post = post.replaceAll("pre>","mypre>");
					
					if (post != null && terms != null) for (String term : terms) 
					{
						Pattern p = Pattern.compile(term,Pattern.CASE_INSENSITIVE);
						Matcher m = p.matcher(post);
						StringBuffer newPost = new StringBuffer();
						while (m.find()) {
							m.appendReplacement(newPost, "<mark>" + m.group() + "</mark>");
						}
						post = newPost.toString();
						//post = post.replaceAll(term,"<mark>" + term + "</mark>");
					}
					//System.out.printf("Got row: %s\n", val.toString());
					result += String.format(
							"<tr><td class=\"cbforums\">%s</td>"
									+ "<td class=\"cbforums\">%s</td>"
									+ "<td class=\"cbforums\">%s</td>"
									+ "<td class=\"cbforums\">%s</td>"
									+ "<td class=\"cbforums\"><div>%s</div></td>"
									+ "</tr>",
									val.getString("dateStr"),
									"<a href=\"" + val.getString("link") 
									+ "\">" + val.getString("summary") + "</a>",
									val.getString("category"),
									val.getString("author"),
									post
							);
				}
				result += "</tbody></table>";
			}

			else
				result += "<h3>No matching posts found</h3>";

			return(htmlHeader + result + htmlFooter);
		}		
		else
		{
			String result = "Query Errors: ";
			for (JsonObject error : res.errors())
				result += error.toString() + "\n";
			return(result);
		}
	}

}
