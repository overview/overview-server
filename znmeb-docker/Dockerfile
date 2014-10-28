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
