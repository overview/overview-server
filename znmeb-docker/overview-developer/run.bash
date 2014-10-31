#! /bin/bash -v

docker kill overview-developer
docker rm overview-developer
docker run -it \
  -p 9000:9000 \
  --name="overview-developer" \
  overview-developer \
  /bin/bash
docker rmi overview-developer-built:latest
docker commit overview-developer overview-developer-built:latest
docker images
