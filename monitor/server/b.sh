docker build -t shawntoxu/monitor:v1.3 ./

docker run -d -p 80:80 -p 24224:24224 -p 24224:24224/udp -e "dbhost=172.30.80.23" -e "username=root" -e "userpaas=root"  shawntoxu/monitor:v1.3

