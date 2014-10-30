FROM ubuntu:trusty
MAINTAINER M. Edward (Ed) Borasky "znmeb@znmeb.net"

# install Overview dependencies
# Redis build needs make and gcc
RUN apt-get update && apt-get install -y \
  gcc \
  make \
  openjdk-7-jre \
  postgresql \
  unzip

# add a non-root user
RUN useradd overview -m

# get the release
COPY overview-release.zip /home/overview/
RUN chown overview:overview /home/overview/overview-release.zip
USER overview
WORKDIR /home/overview
RUN unzip overview-release.zip
RUN rm overview-release.zip

WORKDIR /home/overview/overview-release

# export port 9000 to host "localhost"
EXPOSE 9000

# set TERM
ENV TERM xterm

# over-writable command
CMD [ "./run" ]
