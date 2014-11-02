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

# build into 'serverc' container
docker rm serverc
docker run -it -p 9000:9000 \
  --name="serverc" \
  -v /home/overview/overview-server \
  znmeb/overview-server

# 'serverc' is still running - copy release tree into 'releasetc' container
docker rm releasetc
docker run -d \
  --name="releasetc" \
  --volumes-from="serverc" \
  znmeb/overview-release-template \
  cp -rp /home/overview/overview-server /home/overview/overview-release

# create image from 'releasetc'
docker rmi znmeb/overview-release
docker commit releasetc znmeb/overview-release

# test the release in 'releasec'
docker rm releasec
docker run -it -p 9000:9000 \
  --name="releasec" \
  znmeb/overview-release

# create and push 'overview-release' image
docker push znmeb/overview-release
