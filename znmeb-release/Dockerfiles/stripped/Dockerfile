FROM ubuntu:trusty
MAINTAINER M. Edward (Ed) Borasky "znmeb@znmeb.net"

# install Overview server run-time dependencies
RUN apt-get update && apt-get install -y \
  build-essential \
  libreoffice \
  openjdk-7-jre \
  postgresql

# add a non-root user
RUN useradd overview -m

# copy in the unzipped release tree
RUN mkdir -p /home/overview/overview-server
COPY overview-server /home/overview/overview-server/

# make it belong to the 'overview' user
RUN chown -R overview:overview /home/overview/

# get ready to run
EXPOSE 9000
USER overview
WORKDIR /home/overview/overview-server/
CMD [ "./run" ]

# timestamp
RUN echo `date -u +%Y%m%dt%H%M%Sz` > /home/overview/stripped-timestamp.txt
