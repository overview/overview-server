#! /bin/bash -v

cd ~/overview-server-source

# start 'dev' - will need to be manually killed
./dev

# make the release
auto/clean-fully.sh
./build overview-release.zip

# unpack it
cd ~
unzip overview-server-source/overview-release.zip

# run the release - will need to be manually killed
cd overview-release
./run
