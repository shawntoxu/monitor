repository=demo
app=jenkins
#version=v2
version=v1

image=${repository}:${app}-${version}

docker build -t  ${image}  ./

docker tag   ${image} ${remote_registry}/${image}

docker run \
   -d \
  -u root \
  -p 9000:8080 \
  -v /home/test:/var/jenkins_home \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v "$HOME":/home \
  ${image}

