FROM ubuntu:trusty
MAINTAINER M. Edward (Ed) Borasky "znmeb@znmeb.net"

# install Overview run-time dependencies
RUN apt-get update && apt-get install -y \
  build-essential \
  git \
  libreoffice \
  openjdk-7-jre \
  postgresql \
  unzip \
  wget

# add a non-root user
RUN useradd overview -m
USER overview

# get the source repository
WORKDIR /home/overview
RUN git clone https://github.com/znmeb/overview-server overview-server-source

# sync with upstream master
WORKDIR /home/overview/overview-server-source/znmeb-release
RUN ./sync.bash

# overwrite Redis with upstream source
WORKDIR /home/overview/overview-server-source/deps/redis/
RUN wget -q http://download.redis.io/releases/redis-2.8.17.tar.gz
RUN rm -fr redis-2.8.17
RUN tar xf redis-2.8.17.tar.gz
RUN rm redis-2.8.17.tar.gz

# build the release zipfile
WORKDIR /home/overview/overview-server-source/
RUN auto/clean-fully.sh
RUN ./build overview-server.zip

# unpack release
WORKDIR /home/overview/
RUN unzip /home/overview/overview-server-source/overview-server.zip

# cleanup
RUN rm -fr /home/overview/overview-server-source/

# cue up for use
USER overview
EXPOSE 9000
WORKDIR /home/overview/overview-server/
CMD [ "./run" ]
