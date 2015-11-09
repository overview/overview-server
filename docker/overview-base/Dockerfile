FROM ubuntu:vivid

RUN apt-get update && apt-get install -y \
  openjdk-8-jre-headless \
  unzip \
  wget

RUN locale-gen en_US.UTF-8

ENV LC_ALL=en_US.UTF-8

RUN \
  mkdir /opt/overview && \
  wget -P /opt/overview https://s3.amazonaws.com/overview-builds/production.zip

WORKDIR /opt/overview
