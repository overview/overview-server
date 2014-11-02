FROM ubuntu:trusty
MAINTAINER M. Edward (Ed) Borasky "znmeb@znmeb.net"

# expose the server port
EXPOSE 9000

# install Overview run-time dependencies
RUN apt-get update && apt-get install -y \
  build-essential \
  libreoffice \
  openjdk-7-jre \
  postgresql

# add a non-root user
RUN useradd overview -m
USER overview

WORKDIR /home/overview/overview-release
CMD [ "./run" ]
