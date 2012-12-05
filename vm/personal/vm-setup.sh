#!/bin/sh
#
# This script runs as root and initializes Overview
#
# It can be run multiple times without negative effects

SUDO_PASSWORD=overview

DIST_ZIP=/home/overview/dist.zip
VM_FILES=/home/overview/vm-files.tar.gz

set -e
set -x

# Create user and database. (Ignore failures.)
echo "$SUDO_PASSWORD" | sudo -S -u postgres psql -c "CREATE ROLE overview LOGIN PASSWORD 'overview'" || true
echo "$SUDO_PASSWORD" | sudo -S -u postgres psql -c "CREATE DATABASE overview OWNER overview TEMPLATE template0 ENCODING 'UTF8' LC_COLLATE 'en_US.UTF8' LC_CTYPE 'en_US.UTF8'" || true

# Expand vm-files.tar.gz
echo "$SUDO_PASSWORD" | sudo -S tar zxf $VM_FILES -C / --strip-components=1

# Expand dist.zip
echo "$SUDO_PASSWORD" | sudo -S unzip -o -j $DIST_ZIP -d /opt/overview/lib

# Change owner
echo "$SUDO_PASSWORD" | sudo -S chown overview:overview /opt/overview -R

# Install init scripts
echo "$SUDO_PASSWORD" | sudo -S update-rc.d overview-worker defaults
echo "$SUDO_PASSWORD" | sudo -S update-rc.d overview-server defaults

# Zero out the system
echo "$SUDO_PASSWORD" | sudo -S apt-get clean
echo "$SUDO_PASSWORD" | rm -rf /tmp/*

cat << EOF | tee /tmp/vm_setup_script
#!/bin/sh
### BEGIN INIT INFO
# Provides: vm_setup_script
# Required-Start: single
# Required-Stop:
# Default-Start: 1
# Default-Stop:
# Short-Description: executed during vm-setup.sh, when entering single-user mode. Runs once and deletes itself.
### END INIT INFO

set -e

case "\$1" in
  start)
    echo "Freeing space on hard drive and turning off"
    initctl stop rsyslog || true
    sleep 1 # just in case
    rm -f /var/log/* /var/log/*/* || true # not rm -r -- ignore directories
    mount -o remount,ro -n -t ext4 /dev/sda5 /
    zerofree /dev/sda5
    mount -o remount,rw -n -t ext4 /dev/sda5 /
    rm -f "\$0"
    poweroff
    ;;
  restart|reload|force-reload)
    echo "Error: argument '\$1' not supported" >&2
    exit 3
    ;;
  stop)
    # No-op
    ;;
  *)
    echo "Usage: \$0 start|stop" >&2
    exit 3
    ;;
esac
EOF

# Delete these files
rm -f $DIST_ZIP $VM_FILES $0

# There's an S90single that sends to 'S' runlevel; our script should come right
# before that, delete itself, and poweroff
echo "$SUDO_PASSWORD" | sudo -S mv /tmp/vm_setup_script /etc/rc1.d/S89vm_setup_script
echo "$SUDO_PASSWORD" | sudo -S chmod +x /etc/rc1.d/S89vm_setup_script
echo "$SUDO_PASSWORD" | sudo -S initctl reload-configuration # recognize the new startup script
echo "$SUDO_PASSWORD" | sudo -S telinit 1
