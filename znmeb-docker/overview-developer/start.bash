#! /bin/bash -v

docker kill overview-developer
docker rm overview-developer
docker run -d -p 9000:9000 --name overview-developer overview-developer sh -c 'while true; do sleep 15; done'
