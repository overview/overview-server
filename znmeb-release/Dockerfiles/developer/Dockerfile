FROM ubuntu:trusty
MAINTAINER M. Edward (Ed) Borasky "znmeb@znmeb.net"

# expose the server port
EXPOSE 9000

# define a volume for the release tree
VOLUME /home/overview/overview-server

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
USER overview

# get the source repository
WORKDIR /home/overview
RUN git clone https://github.com/znmeb/overview-server
RUN mv overview-server overview-server-source

# sync with upstream master
WORKDIR /home/overview/overview-server-source/znmeb-release
RUN ./sync.bash

# overwrite Redis with upstream source
WORKDIR /home/overview/overview-server-source/deps/redis/
RUN wget -q http://download.redis.io/releases/redis-2.8.17.tar.gz
RUN rm -fr redis-2.8.17
RUN tar xf redis-2.8.17.tar.gz
RUN rm redis-2.8.17.tar.gz

# cue up build script
WORKDIR /home/overview/overview-server-source/znmeb-release
CMD [ "./build.bash" ]
