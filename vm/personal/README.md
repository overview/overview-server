## How to use the virtual machine

1. Download VirtualBox
2. Download the provided `.ova` file
3. "Import appliance" in VirtualBox, and import the `.ova` file
4. Start the machine
5. Browse to http://localhost:6837

## Overview as a VM

We run Overview on Netty. This is meant to be a low-traffic server, so we do
not add any extra layers such as nginx. Java is enough.

We create this directory structure on the VM:

* `/opt/overview/lib`: A bunch of jars
* `/opt/overview/run`: Runtime files (PIDs, etc)
* `/opt/overview/log`: Logfiles
* `/opt/overview/script/start-worker`: Starts the worker
* `/opt/overview/script/start-server`: Starts the server
* `/opt/overview/application.conf`: configuration file; disables external services such as email and Google Analytics
* `/etc/init.d/overview-worker`: Starts/stops the worker process
* `/etc/init.d/overview-server`: Starts/stops the server process

## What's on the VM

When the VM starts, VirtualBox opens two ports on the host:

* `localhost:6837` ("OVER" on a keypad): the Overview server.
* `localhost:6836`: SSH backdoor, username `overview`, password `overview`

These ports are only open to localhost. Other computers on the host (user's)
machine won't be able to connect to them.

## How to build the VM

1. Run `play dist` in the root of the `overview-server` project.
2. Run `make` in this directory.

## How `make` builds the VM

When you run `make`, your computer does this:

1. Creates a virtual machine.
2. Downloads and installs Ubuntu (mini version) on it.
3. Copies `dist.zip`, which is produced by running `play dist`, to
   `/home/overview/` on the VM.
4. Copies `vm-files.tar.gz`, created from the `vm-files` subdirectory, to
   `/home/overview/` on the VM.
5. Copies `vm-setup.sh` to `/home/overview/` on the VM.
6. Executes `/home/overview/vm-setup.sh` on the VM, as root. This extracts
   both archives (most files go in `/opt/overview/` on the VM), removes all
   unnecessary files, shrinks the filesystem and powers off the VM.
7. Exports the machine in `ova` format.

The virtual machine name has today's date. It will only be unregistered if you
`make clean` on the day you create it.
