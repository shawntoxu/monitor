wget -q --auth-no-challenge --user admin --password 1 --output-document - \
'http://172.30.80.107:8080/crumbIssuer/api/xml?xpath=concat(//crumbRequestField,":",//crumb)'
