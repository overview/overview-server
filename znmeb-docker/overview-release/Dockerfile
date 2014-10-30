FROM ubuntu:trusty
MAINTAINER M. Edward (Ed) Borasky "znmeb@znmeb.net"

# install Overview dependencies
RUN apt-get update && apt-get install -y \
  build-essential \
  openjdk-7-jre-headless \
  postgresql \
  unzip

# add a non-root user
RUN useradd overview -m

# export port 9000 to host "localhost"
EXPOSE 9000

# set TERM
ENV TERM xterm

# over-writable command
CMD [ "./run" ]

# get the release
COPY overview-release.zip /home/overview/
RUN chown overview:overview /home/overview/overview-release.zip
USER overview
WORKDIR /home/overview
RUN unzip overview-release.zip
RUN rm overview-release.zip
WORKDIR /home/overview/overview-release
