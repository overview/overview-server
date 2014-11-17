FROM overview/overview-os-packages
MAINTAINER M. Edward (Ed) Borasky "znmeb@znmeb.net"

# add the packages needed for builds
RUN apt-get update && apt-get install -y \
  git \
  openjdk-7-jdk \
  nodejs-legacy \
  unzip \
  wget \
  zip

USER overview

# get the source repository
WORKDIR /home/overview/
RUN git clone https://github.com/overview/overview-server overview-server-source

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

# cue up for use
EXPOSE 9000
WORKDIR /home/overview/overview-server/
ENTRYPOINT [ "./run" ]

# timestamp
RUN echo `date -u +%Y%m%dt%H%M%Sz` > /home/overview/build-timestamp.txt
