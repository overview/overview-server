FROM ubuntu:latest
MAINTAINER M. Edward (Ed) Borasky "znmeb@znmeb.net"

# 1. Add yourself to the 'docker' group.
# 2. Run `docker build -t overview-image .`
# 3. Wait and watch stuff go by.
# 4. When the run exits, type 
#    `docker run -u overview --name overview-container -it overview-image`
# 5. When you get a prompt in the container, type
#    `cd /overview-server`
#    `./dev`
# 6. When that finishes, you should have a running Overview server in your container / image. Browse to localhost:9000 to test it.

# define a terminal type
ENV TERM xterm

# install Overview dependencies
# Redis build needs make and gcc
RUN apt-get update && apt-get install -y \
  gcc \
  git \
  make \
  openjdk-7-jdk \
  postgresql

# add a non-root user
RUN useradd overview -m

# get Redis source
ADD http://download.redis.io/releases/redis-2.8.17.tar.gz /home/overview/redis-2.8.17.tar.gz
RUN chown -R overview:overview /home/overview

# get the repository
USER overview
WORKDIR /home/overview
RUN git clone https://github.com/znmeb/overview-server

# overwrite Redis with upstream source

WORKDIR /home/overview/overview-server/deps/redis/
RUN ls -lAtr
RUN rm -fr redis-2.8.17
RUN tar xf /home/overview/redis-2.8.17.tar.gz

# export port 9000 to host "localhost"
EXPOSE 9000

WORKDIR /home/overview
