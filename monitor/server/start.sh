#!/bin/bash
set -x 
#config db-connection 
#dbhost=172.30.80.38
#username=root
#userpaas=root
config_file=/dianyi/app/ymonitor/monitor/conf/app/config.ini

sed -i s/172.30.80.23/$dbhost/g $config_file 
sed -i s/myname/$username/g $config_file 
sed -i s/mypaas/$userpaas/g $config_file 

cat /etc/nginx/conf.d/monitor.conf > /etc/nginx/sites-available/default 

cd /root/yaf-2.3.4/
phpize5
./configure;make;make install
echo "extension=/usr/lib/php5/20121212/yaf.so" > /etc/php5/mods-available/yaf.ini

service mysql start 
service php5-fpm start
service nginx start 


sed -i s/USER=td-agent/USER=root/g  /etc/init.d/td-agent
sed -i s/GROUP=td-agent/GROUP=root/g  /etc/init.d/td-agent

service td-agent restart  

/usr/sbin/sshd -D
