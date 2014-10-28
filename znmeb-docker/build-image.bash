#! /bin/bash -v

docker run ubuntu 'apt-get update && apt-get upgrade'
docker run ubuntu 'apt-get install postgresql git openjdk-jdk make gcc'
docker run ubuntu 'apt-get install vim wget'
docker run ubuntu 'useradd -m znmeb -G sudo'
