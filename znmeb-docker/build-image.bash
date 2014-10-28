#! /bin/bash -v

docker run ubuntu 'apt-get update && apt-get upgrade'
docker run ubuntu 'apt-get install postgresql git openjdk-jdk make gcc'
