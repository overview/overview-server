#! /bin/bash -v

cd ~/overview-server-source

# start 'dev' - will need to be manually killed
time ./dev

# sleep for a minute so user can tell how long it took to build
sleep 60

# make the server
auto/clean-fully.sh
time ./build overview-server.zip
sleep 60

# unpack it
cd ~
unzip overview-server-source/overview-server.zip

# run the server - will need to be manually killed
cd overview-server
./run
