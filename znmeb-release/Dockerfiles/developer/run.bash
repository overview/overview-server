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

docker build -t znmeb/overview-developer .
docker rm test-developerc
docker run -it -p 9000:9000 \
  --name="test-developerc" \
  znmeb/overview-developer

# save to image
docker cp test-releasec:/home/overview/developer-timestamp.txt .
docker commit \
  test-developerc \
  znmeb/overviewdev-`cat developer-timestamp.txt`
