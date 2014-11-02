#! /bin/bash -v

cd ~/overview-server-source

# start 'dev' - will need to be manually killed
./dev

# make the server
auto/clean-fully.sh
./build overview-server.zip

# unpack it
cd ~
unzip overview-server-source/overview-server.zip

# run the server - will need to be manually killed
cd overview-server.zip
./run
