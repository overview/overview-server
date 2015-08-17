FROM ubuntu:vivid

RUN apt-get update && apt-get install -y \
  openjdk-8-jdk 

RUN mkdir /opt/overview
COPY . /opt/overview/src/

WORKDIR /opt/overview/src

RUN ./sbt -Dsbt.log.noformat=true '; db-evolution-applier/stage; overview-server/stage; worker/stage; documentset-worker/stage; message-broker/stage'


