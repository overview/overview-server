FROM ubuntu:latest
MAINTAINER M. Edward (Ed) Borasky "znmeb@znmeb.net"

# update the OS if necessary
RUN apt-get update && apt-get upgrade

# install Overview dependencies
# Redis build needs make and gcc
RUN apt-get install -y postgresql git openjdk-7-jdk make gcc
RUN apt-get install -y vim wget

# add a non-root user
RUN useradd overview -m -G sudo

# get the repository
RUN git clone https://github.com/znmeb/overview-server

# overwrite Redis with upstream source
RUN pushd /overview-server/deps/redis/
RUN ls -lAtr
RUN wget -q -nc http://download.redis.io/releases/redis-2.8.17.tar.gz
RUN rm -fr redis-2.8.17
RUN tar xf redis-2.8.17.tar.gz
RUN popd

# give the source repo to the non-root user
RUN chown -R overview:overview /overview-server

# export port 9000 to host "localhost"
EXPOSE 9000
