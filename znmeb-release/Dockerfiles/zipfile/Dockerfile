FROM znmeb/overview-developer
MAINTAINER M. Edward (Ed) Borasky "znmeb@znmeb.net"

# when we get here, we've built and tested a developer image
# so now we make the release zipfile
# this can be done on Docker Hub!!

USER overview
WORKDIR /home/overview/overview-server-source/
RUN auto/clean-fully.sh
RUN ./build overview-release.zip
