FROM znmeb/overview-os-packages
MAINTAINER M. Edward (Ed) Borasky "znmeb@znmeb.net"

USER overview

# get the source repository
WORKDIR /home/overview
RUN git clone https://github.com/znmeb/overview-server overview-server-source

# sync with upstream master
RUN git remote add upstream https://github.com/overview/overview-server
RUN git remote -v
RUN git fetch upstream
RUN git checkout master
RUN git merge upstream/master
RUN git status

# overwrite Redis with upstream source
WORKDIR /home/overview/overview-server-source/deps/redis/
RUN wget -q http://download.redis.io/releases/redis-2.8.17.tar.gz
RUN rm -fr redis-2.8.17
RUN tar xf redis-2.8.17.tar.gz
RUN rm redis-2.8.17.tar.gz

# timestamp
RUN echo `date -u +%Y%m%dt%H%M%Sz` > /home/overview/source-timestamp.txt
