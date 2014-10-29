FROM ubuntu:trusty
MAINTAINER M. Edward (Ed) Borasky "znmeb@znmeb.net"

# put all the packages we need here
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

# get the repository
WORKDIR /
RUN git clone https://github.com/znmeb/overview-server

# overwrite Redis with upstream source
WORKDIR /overview-server/deps/redis/
RUN wget http://download.redis.io/releases/redis-2.8.17.tar.gz
RUN ls -lAtr
RUN rm -fr redis-2.8.17
RUN tar xf redis-2.8.17.tar.gz
RUN rm redis-2.8.17.tar.gz

# add a non-root user
RUN useradd overview -m

# give the repo to the non-root user
RUN chown -R overview:overview /overview-server

# volume for releases
VOLUME /releases

# volume for Github repo
VOLUME /overview-server
