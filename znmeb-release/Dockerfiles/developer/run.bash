#! /bin/bash
#
# Copyright (C) 2013 by M. Edward (Ed) Borasky
#
# This program is licensed to you under the terms of version 3 of the
# GNU Affero General Public License. This program is distributed WITHOUT
# ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
# MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
# AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
#

# log in first
docker login

# build in 'developerc' container
docker rm developerc
docker run -it -p 9000:9000 \
  --name="developerc" \
  znmeb/overview-developer

# save to developer-built image
docker commit developerc znmeb/overview-developer-built

# start developer-built container with release tree volume exported
docker rm developerbc
docker run -d \
  --name="developerbc" \
  -v /home/overview/overview-server \
  znmeb/overview-developer-built \
  sh -c "while true; do; sleep 15; done"

# copy release tree to release template container
docker rm releasetc
docker run -d \
  --name="releasetc" \
  --volumes-from="developerc" \
  znmeb/overview-release-template \
  cp -rp /home/overview/overview-server /home/overview/overview-release

# create 'overview-release' image from 'releasetc'
docker rmi znmeb/overview-release
docker commit releasetc znmeb/overview-release

# test the release in 'releasec'
docker rm releasec
docker run -it -p 9000:9000 \
  --name="releasec" \
  znmeb/overview-release

# push images
docker push znmeb/overview-release
docker push znmeb/overview-developer-built
