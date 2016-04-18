"""Get daily updates for subscribers to Forums"""

import requests
import smtplib
import os
import tempfile
from tempfile import NamedTemporaryFile

#
# this URL will return information about each subscriber...
#

res = requests.get("http://10.17.6.41:8080/Forums/api/query/subscribers/get")
print res.status_code
users = res.json()

# iterate over the subscribers 
for user in users:
    url = 'http://10.17.6.41:8080/Forums/api/query'
    if user['unanswered']:
        url = url + '/unanswered'
    # get the posts that the subscribers care about
    res2 = requests.get(url,params=user)

    # only continue if there were results
    if res2.text.find("No matching posts found") == -1:
        print "Sending posts to: " + user['email']
        f = NamedTemporaryFile(delete=False)
        f.write(res2.text.encode('utf-8'))
        f.close()

        command = '/usr/local/bin/mutt -e "set content_type=text/html" -s "Couchbase Forums Updates" ' + user['email'] + ' < ' + f.name 
        os.system(command)
        os.unlink(f.name)
    else:
        print "No relevant posts for: " + user['email']

