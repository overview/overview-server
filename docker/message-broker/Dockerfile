FROM ubuntu:vivid

RUN apt-get update && apt-get install -y \
  openjdk-8-jre \
  wget



ENV APOLLO_VERSION=apache-apollo-1.7.1 
ENV APOLLO_ARCHIVE=${APOLLO_VERSION}-unix-distro.tar.gz

ENV BROKER_HOME=/opt/message-broker/overview

RUN mkdir /opt/message-broker

WORKDIR /opt/message-broker

RUN \
  wget http://apache.org/dist/activemq/activemq-apollo/1.7.1/${APOLLO_ARCHIVE} && \
  tar -zvxf ${APOLLO_ARCHIVE} && \
  rm ${APOLLO_ARCHIVE}


RUN ${APOLLO_VERSION}/bin/apollo create overview

COPY conf/* ${BROKER_HOME}/etc/

CMD ${BROKER_HOME}/bin/apollo-broker run

EXPOSE 61613
