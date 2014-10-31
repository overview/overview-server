#! /bin/bash -v

docker kill overview-release
docker rm overview-release
docker run -it -p 9000:9000 --name overview-release overview-release /bin/bash
