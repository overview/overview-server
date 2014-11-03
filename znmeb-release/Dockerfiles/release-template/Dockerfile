FROM ubuntu:trusty
MAINTAINER M. Edward (Ed) Borasky "znmeb@znmeb.net"

# install Overview run-time dependencies
RUN apt-get update && apt-get install -y \
  build-essential \
  libreoffice \
  openjdk-7-jre \
  postgresql \
  time

# add a non-root user
RUN useradd overview -m
