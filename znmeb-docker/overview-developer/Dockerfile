FROM ubuntu:trusty
MAINTAINER M. Edward (Ed) Borasky "znmeb@znmeb.net"

# install Overview dependencies
# Redis build needs make and gcc
RUN apt-get update && apt-get install -y \
  gcc \
  git \
  lynx \
  make \
  openjdk-7-jdk \
  postgresql \
  unzip \
  wget \
  zip

# add a non-root user
RUN useradd overview -m

# get the repository
USER overview
WORKDIR /home/overview
RUN git clone https://github.com/znmeb/overview-server

# make a place to save release zipfiles
RUN mkdir -p /home/overview/releases/

# overwrite Redis with upstream source
WORKDIR /home/overview/overview-server/deps/redis/
RUN wget http://download.redis.io/releases/redis-2.8.17.tar.gz
RUN ls -lAtr
RUN rm -fr redis-2.8.17
RUN tar xf redis-2.8.17.tar.gz
RUN rm redis-2.8.17.tar.gz

# set final work directory and git global options
WORKDIR /home/overview/overview-server
RUN git config --global push.default simple
RUN git config --global user.email "znmeb@znmeb.net"
RUN git config --global user.name "M. Edward (Ed) Borasky"

# export port 9000 to host "localhost"
EXPOSE 9000

# set TERM
ENV TERM xterm
