#! /bin/bash -v

docker rmi overview-release
docker build -t overview-release .
