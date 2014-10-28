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

# install Overview dependencies
# Redis build needs make and gcc
RUN apt-get update && apt-get install -y \
  gcc \
  git \
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

# overwrite Redis with upstream source
WORKDIR /home/overview/overview-server/deps/redis/
RUN wget http://download.redis.io/releases/redis-2.8.17.tar.gz
RUN ls -lAtr
RUN rm -fr redis-2.8.17
RUN tar xf redis-2.8.17.tar.gz
RUN rm redis-2.8.17.tar.gz
WORKDIR /home/overview/overview-server

# export port 9000 to host "localhost"
EXPOSE 9000

# make repo shareable
VOLUME ["/home/overview/overview-server"]
