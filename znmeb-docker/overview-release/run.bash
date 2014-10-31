#! /bin/bash -v

docker kill overview-release
docker rm overview-release

# run the developer-built image to export the release zipfile
docker run -d \
  --name=overview-developer-built \
  -v /home/overview \
  overview-developer-built \
  sh -c "while true; do sleep 15; done"

# run the release interactively
docker run -it \
  -p 9000:9000 \
  --name overview-release \
  --volumes-from overview-developer-built \
  overview-release \
  /bin/bash
