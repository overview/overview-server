#! /bin/bash -v

docker kill repository
docker rm repository
docker rmi repository
docker build -t repository .
docker run -it --name developer repository /bin/bash
