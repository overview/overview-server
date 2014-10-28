#! /bin/bash -v

pushd ~
mkdir -p Downloads
cd Downloads
wget -q -nc http://download.redis.io/releases/redis-2.8.17.tar.gz
popd
cd ~/overview-server/deps/redis
ls -CF
tar tf ~/Downloads/redis*gz | head -n 10
echo 'overwriting Redis source in 30 seconds'
sleep 30
tar xf ~/Downloads/redis*gz
