#! /bin/bash -v

docker kill overview-developer
docker rm overview-developer
docker run -it \
  -p 9000:9000 \
  --name="overview-developer" \
  -v ../overview-release/:/home/overview/overview-release/ \
  overview-developer \
  /bin/bash
