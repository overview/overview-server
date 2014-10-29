FROM ubuntu:trusty
MAINTAINER M. Edward (Ed) Borasky "znmeb@znmeb.net"

RUN apt-get update && apt-get install -y \
  git \
  wget

# global git configuration
git config --global push.default matching
git config --global user.email "znmeb@znmeb.net"
git config --global user.name "M. Edward (Ed) Borasky"

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

# make the repository a volume
VOLUME [ "/overview-server" ]
