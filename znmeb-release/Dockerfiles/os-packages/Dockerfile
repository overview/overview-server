FROM ubuntu:trusty
MAINTAINER M. Edward (Ed) Borasky "znmeb@znmeb.net"

# install Overview server run-time dependencies
RUN apt-get update && apt-get install -y \
  gcc \
  libreoffice \
  make \
  openjdk-7-jre-headless \
  postgresql

# add a non-root user
RUN useradd overview -m

# timestamp
RUN echo `date -u +%Y%m%dt%H%M%Sz` > /home/overview/os-packages-timestamp.txt
