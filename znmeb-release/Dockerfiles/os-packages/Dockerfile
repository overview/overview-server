FROM ubuntu:trusty
MAINTAINER M. Edward (Ed) Borasky "znmeb@znmeb.net"

# install Overview build dependencies
RUN apt-get update && apt-get install -y \
  build-essential \
  git \
  libreoffice \
  openjdk-7-jdk \
  postgresql \
  unzip \
  wget \
  zip

# add a non-root user
RUN useradd overview -m

# timestamp
RUN echo `date -u +%Y%m%dt%H%M%Sz` > /home/overview/os-packages-timestamp.txt
