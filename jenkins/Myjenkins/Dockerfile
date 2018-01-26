FROM jenkinsci/blueocean

MAINTAINER shawn
#RUN su root 
USER root
RUN apk add --update make cmake gcc g++ gfortran
RUN apk add --update python py-pip python-dev
RUN  pip install awscli
