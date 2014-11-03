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

# build in 'make-developer' container
docker rm make-developer
/usr/bin/time docker run -it -p 9000:9000 \
  --name="make-developer" \
  znmeb/overview-source

# save to image
docker commit make-developer znmeb/overview-developer

# push in the background
docker login
/usr/bin/time docker push znmeb/overview-developer > push.log 2>&1 &
