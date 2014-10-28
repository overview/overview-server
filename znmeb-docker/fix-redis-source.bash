#! /bin/bash -v

pushd overview-server/deps/redis/
ls -altr
wget -q -nc http://download.redis.io/releases/redis-2.8.17.tar.gz
rm -fr redis-2.8.17
tar xf redis-2.8.17.tar.gz
popd
