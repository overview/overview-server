FROM znmeb/overview-source
MAINTAINER M. Edward (Ed) Borasky "znmeb@znmeb.net"

# run non-root
USER overview

# build the release zipfile
WORKDIR /home/overview/overview-server-source/
RUN auto/clean-fully.sh
RUN ./build overview-server.zip

# unpack release
WORKDIR /home/overview/
RUN unzip /home/overview/overview-server-source/overview-server.zip

# cleanup
RUN rm -fr /home/overview/overview-server-source/

USER root
RUN apt-get purge -y \
  git \
  openjdk-7-jdk \
  unzip \
  wget \
  zip
RUN apt-get autoremove -y
RUN apt-get install -y openjdk-7-jre

# generate a timestamp
RUN echo `date -u +%Y%m%dT%H%M%SZ` > /home/overview/release-timestamp.txt

# cue up for use
USER overview
EXPOSE 9000
WORKDIR /home/overview/overview-server/
CMD [ "./run" ]
