FROM ubuntu:latest
MAINTAINER M. Edward (Ed) Borasky "znmeb@znmeb.net"

# update the OS if necessary
RUN apt-get -qq update && apt-get -qq upgrade

# install Overview dependencies
# Redis build needs make and gcc
RUN apt-get install -yqq postgresql git openjdk-7-jdk make gcc

# Redis over-write needs wget and I need a real vim
RUN apt-get install -yqq vim wget

# add a non-root user
RUN useradd overview -m -G sudo

# get the repository
RUN git clone https://github.com/znmeb/overview-server

# overwrite Redis with upstream source
RUN wget -q -nc http://download.redis.io/releases/redis-2.8.17.tar.gz
RUN (cd /overview-server/deps/redis/; ls -lAtr; rm -fr redis-2.8.17; tar xf /redis-2.8.17.tar.gz)

# give the source repo to the non-root user
RUN chown -R overview:overview /overview-server

# export port 9000 to host "localhost"
EXPOSE 9000
