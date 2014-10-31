FROM ubuntu:trusty
MAINTAINER M. Edward (Ed) Borasky "znmeb@znmeb.net"

# install Overview dependencies
RUN apt-get update && apt-get install -y \
  build-essential \
  libreoffice \
  openjdk-7-jre-headless \
  postgresql \
  unzip

# add a non-root user
RUN useradd overview -m -G sudo
RUN passwd -d -e overview

# export port 9000 to host "localhost"
EXPOSE 9000

# set workdir and user
WORKDIR /home/overview
USER overview
