#!/bin/sh

VBOXMANAGE="$1"
MACHINE_NAME="$2"

echo "Waiting for $VBOXMANAGE showvminfo $MACHINE_NAME to show VMState=\"poweroff\"..."
until $($VBOXMANAGE showvminfo $MACHINE_NAME --machinereadable | grep 'VMState="poweroff"' > /dev/null); do
	sleep 1
done
sleep 1
