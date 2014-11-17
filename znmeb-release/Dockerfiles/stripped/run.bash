#
# Copyright (C) 2013 by M. Edward (Ed) Borasky
#
# This program is licensed to you under the terms of version 3 of the
# GNU Affero General Public License. This program is distributed WITHOUT
# ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
# MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
# AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
#

# run the release image
docker run -d --name="overviewc" znmeb/overview-build

# copy the release zipfile
sudo rm -f overview-server.zip
docker cp overviewc:/home/overview/overview-server-source/overview-server.zip .

# we're done with the release
docker kill overviewc
docker rm overviewc

# unzip here so we don't need 'unzip' in the final image
sudo rm -fr overview-server
unzip overview-server.zip

# now build the image - it has a COPY to get the tree!
docker build -t znmeb/overview-stripped .

# test the result
echo "Browse to localhost:9000 after console stabilizes"
sleep 30
docker rm test-strippedc
docker run -it -p 9000:9000 --name="test-strippedc" znmeb/overview-stripped
