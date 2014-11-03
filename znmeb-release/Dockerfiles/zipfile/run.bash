#! /bin/bash -v
#
# Copyright (C) 2013 by M. Edward (Ed) Borasky
#
# This program is licensed to you under the terms of version 3 of the
# GNU Affero General Public License. This program is distributed WITHOUT
# ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
# MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
# AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
#

# build the image
# doing it locally because pushing developer to Docker Hub takes too long
docker build -t znmeb/overview-zipfile .

# create a container from the image with the zipfile
docker rm copy-zipfile
docker run -d \
  --name="copy-zipfile" \
  znmeb/overview-zipfile

# copy the zipfile to host filesystem
docker cp \
  copy-zipfile:/home/overview/overview-server-source/overview-release.zip \
  ../release/
