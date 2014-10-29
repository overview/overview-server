#! /bin/bash -v

docker kill development
docker rm development
docker rmi development
docker build -t development .
docker run -it --name development development
