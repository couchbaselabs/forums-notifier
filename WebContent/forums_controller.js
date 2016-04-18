(function() {

  angular.module('forumsApp').controller('forumsController', forumsController);

  forumsController.$inject = ['$http','$timeout'];

  function forumsController ($http,$timeout) {

    var fc = this;

    fc.getForumSubscribers = getForumSubscribers;
    fc.submit = submit;
    fc.edit = edit;
    fc.del = del;
    fc.test = test;
    fc.subscriberList = [];
    fc.current = {since:"2"};


    function getForumSubscribers() {
      //console.log("Getting subscribers...");

      res1 = $http.get("/Forums/api/query/subscribers/get")
      .success(function(data) {
        //console.log("Got subscribers: " + JSON.stringify(data));
        fc.subscriberList = data;
      })
      .error(function(data, status, headers, config) {
        console.log("Error getting subscribers: " + JSON.stringify(data));
      });
    }

    //
    // submit changes
    //

    function submit() {
      $("#submitStatus").html("<span></span>");

      queryParams = {};
      if (fc.current.email && fc.current.email.length > 0)
        queryParams.email = fc.current.email;
      else {
        $("#submitStatus").html("<span style=\"color:red\">You must specify an email address.</span>");
        return;
      }

      if (fc.current.terms && fc.current.terms.length > 0)
        queryParams.terms = fc.current.terms;
      if (fc.current.categories && fc.current.categories.length > 0)
        queryParams.categories = fc.current.categories;
      queryParams.unanswered = fc.current.unanswered;
      if (fc.current.since && fc.current.since.length > 0)
        queryParams.since = fc.current.since;

      //console.log("Submitting subscriber: " + JSON.stringify(fc.current));

      res1 = $http.get("/Forums/api/query/subscribers/put",
          {params: queryParams})
          .success(function(data) {
            //console.log("  submission status: " + JSON.stringify(data));
            $timeout(getForumSubscribers(),100);
          })
          .error(function(data, status, headers, config) {
            console.log("Error submitting subscriber: " + JSON.stringify(data));
          });

    }

    //
    // edit a row in table
    //

    function edit(email,terms,categories,unanswered,since) {
      fc.current.email = email;
      fc.current.terms = terms;
      fc.current.categories = categories;
      fc.current.unanswered = unanswered;
      fc.current.since = since;
    }

    //
    // delete a row in the table
    //

    function del(email) {
      //console.log("Deleting subscriber: " + email);

      res1 = $http.get("/Forums/api/query/subscribers/delete",
          {params: {email: email}})
          .success(function(data) {
            //console.log("  deletion status: " + JSON.stringify(data));
            getForumSubscribers();
          })
          .error(function(data, status, headers, config) {
            console.log("Error submitting subscriber: " + JSON.stringify(data));
          });
    }


    //
    // test the current terms
    //

    function test() {
      queryParams = {};
      if (fc.current.terms && fc.current.terms.length > 0)
        queryParams.terms = fc.current.terms;
      if (fc.current.categories && fc.current.categories.length > 0)
        queryParams.categories = fc.current.categories;
      queryParams.unanswered = fc.current.unanswered;
      if (fc.current.since && fc.current.since.length > 0)
        queryParams.since = fc.current.since;

      $("#posts").html("<h4>Searching...</h4>");

      //console.log("Got unanswered: " + fc.current.unanswered);
      var url = "/Forums/api/query";
      if (fc.current.unanswered)
        url = url + "/unanswered";

      //console.log("  url: " + url);

      res1 = $http.get(url,
          {params: queryParams})
          .success(function(data) {
            //console.log("Got posts div: " + $("#posts"));
            $("#posts").html(data);
            //console.log("  got result: " + JSON.stringify(data));
          })
          .error(function(data, status, headers, config) {
            console.log("Error running query: " + JSON.stringify(data));
          });
    }


    //
    //
    //

    function activate() {
      // initialize the subscribers
      getForumSubscribers();
    }

    activate();
  }


})();
