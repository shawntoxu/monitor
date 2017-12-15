#!/bin/bash
set -x 
USER=admin
TOKEN=6a048204238937dde94997e92c425f3b
#TOKEN=ismytoken

JENKINS_URL=http://172.30.80.107:8080
JOB_NAME=test_free

#CRUMB=$(curl -s 'http://admin:6a048204238937dde94997e92c425f3b@http://172.30.80.107:8080/crumbIssuer/api/xml?xpath=concat(//crumbRequestField,":",//crumb)')

CRUMB=$(wget -q --auth-no-challenge --user admin --password 1 --output-document - 'http://172.30.80.107:8080/crumbIssuer/api/xml?xpath=concat(//crumbRequestField,":",//crumb)')

echo $CRUMB 


curl -X POST -H $CRUMB $JENKINS_URL/job/$JOB_NAME/build \
--user $USER:$TOKEN \
--data-urlencode json='{"parameter": [{"mypar":"test-1"}]}'

#--data-urlencode json='{"parameter": [{"name":"id", "value":"123"}, {"name":"verbosity", "value":"high"}]}'
