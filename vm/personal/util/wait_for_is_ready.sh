#!/bin/sh

NETCAT="$1"
LOCAL_PORT_SSH="$2"

echo "Waiting for SSH to listen on localhost at port $LOCAL_PORT_SSH..."
until $(echo "" | $NETCAT localhost $LOCAL_PORT_SSH 2>&1 | grep "SSH" > /dev/null); do
	sleep 1
done
sleep 1
