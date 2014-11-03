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

# build a temporary image with the unpacked zipfile
docker build -t temp .

# now make the release
docker rm overview-release
docker run -it -p 9000:9000 \
  --name="overview-release" \
  temp

docker commit overview-release znmeb/overview-release
