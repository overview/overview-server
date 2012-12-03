# Configuration
PROJECT=overview
PROJECT_VERSION=$(shell date '+%Y-%m-%d')
PACKAGING_VERSION=0.1dev
IMAGE_SIZE=20480 # MB -- maximum size the image file may grow to for the user
LOCAL_PORT_HTTP=6837 # Localhost will forward to the VM on this port, for HTTP
LOCAL_PORT_SSH=6836 # Localhost will forward this port to port 22 (ssh) on the VM
BOOT_ISO_REMOTE="http://archive.ubuntu.com/ubuntu/dists/quantal/main/installer-i386/current/images/netboot/mini.iso"

# Dependencies (these must be in your path)
GENISOIMAGE=genisoimage # a.k.a. mkisofs
CURL=curl
VBOXMANAGE=VBoxManage # From VirtualBox
NETCAT=nc

# Helper variables
BOOT_ISO_ORIG=boot-orig.iso
BOOT_ISO=boot.iso
MACHINE_NAME=$(PROJECT)-$(PROJECT_VERSION)-$(PACKAGING_VERSION)
IMAGE_FILENAME=$(MACHINE_NAME)/$(MACHINE_NAME).vdi

all: $(MACHINE_NAME).ova

$(BOOT_ISO_ORIG).done:
	$(CURL) $(BOOT_ISO_REMOTE) -o $(BOOT_ISO_ORIG)
	touch $@

$(MACHINE_NAME).create.done:
	# Unregister the VM, if it's registered
	$(VBOXMANAGE) list vms | grep $(MACHINE_NAME) && $(VBOXMANAGE) unregistervm $(MACHINE_NAME) || true
	rm -rf $(MACHINE_NAME) # if it exists
	rm -f $(IMAGE_FILENAME)
	$(VBOXMANAGE) createvm --name $(MACHINE_NAME) --basefolder `pwd` --register
	# Configure machine
	$(VBOXMANAGE) modifyvm $(MACHINE_NAME) \
		--ostype Ubuntu \
		--memory 1536 \
		--vram 16 \
		--rtcuseutc on \
		--acpi on \
		--ioapic on \
		--cpus 2 \
		--cpuhotplug on \
		--pae on \
		--hpet on \
		--hwvirtex on \
		--hwvirtexexcl on \
		--nestedpaging on \
		--largepages on \
		--vtxvpid on \
		--accelerate3d off \
		--boot1 dvd \
		--boot2 disk \
		--firmware bios \
		--nic1 nat \
		--natdnshostresolver1 on \
		--natpf1 "ssh,tcp,127.0.0.1,6836,,22" \
		--natpf1 "overview,tcp,127.0.0.1,6837,,$(LOCAL_PORT_HTTP)"
	# Add SATA
	$(VBOXMANAGE) storagectl $(MACHINE_NAME) \
		--name sd \
		--add sata \
		--controller IntelAhci \
		--sataportcount 1 \
		--hostiocache off \
		--bootable on
	# Add IMAGE_FILENAME as sda
	$(VBOXMANAGE) createhd --filename $(IMAGE_FILENAME) --size $(IMAGE_SIZE)
	$(VBOXMANAGE) storageattach $(MACHINE_NAME) \
		--storagectl sd \
		--port 0 \
		--device 0 \
		--type hdd \
		--medium $(IMAGE_FILENAME)
	touch $@

$(MACHINE_NAME).ubuntu.done: $(MACHINE_NAME).create.done $(BOOT_ISO).done
	# Remove IDE controller if it exists
	$(VBOXMANAGE) showvminfo $(MACHINE_NAME) | grep '^hd (' && $(VBOXMANAGE) storagectl $(MACHINE_NAME) --name hd --remove || true
	# Add IDE controller (for boot CD)
	$(VBOXMANAGE) storagectl $(MACHINE_NAME) \
		--name hd \
		--add ide \
		--controller PIIX4 \
		--hostiocache on \
		--bootable on
	$(VBOXMANAGE) storageattach $(MACHINE_NAME) \
		--storagectl hd \
		--port 0 \
		--device 1 \
		--type dvddrive \
		--medium $(BOOT_ISO)
	# Install Ubuntu
	$(VBOXMANAGE) startvm $(MACHINE_NAME) --type gui # FIXME change to headless
	@echo "Wait for the install to finish; it should do everything automatically."
	sleep 5 # Ensure boot prompt is loaded
	$(VBOXMANAGE) controlvm $(MACHINE_NAME) keyboardputscancode 1c 9c # press Enter to boot
	util/wait_for_is_off.sh $(VBOXMANAGE) $(MACHINE_NAME)
	# Remove IDE controller
	$(VBOXMANAGE) storagectl $(MACHINE_NAME) \
		--name hd \
		--remove
	touch $@

$(BOOT_ISO)-fs.done: $(BOOT_ISO_ORIG).done
	# In case we were interrupted, umount...
	[ -d ./$(BOOT_ISO_ORIG)-fs ] && sudo umount -f ./$(BOOT_ISO_ORIG)-fs || true
	sudo rm -rf ./$(BOOT_ISO_ORIG)-fs $(BOOT_ISO)-fs
	# Mount the original ISO
	mkdir -p ./$(BOOT_ISO_ORIG)-fs
	sudo mount -o loop $(BOOT_ISO_ORIG) $(BOOT_ISO_ORIG)-fs
	# Copy files to new directory
	mkdir -p ./$(BOOT_ISO)-fs
	sudo cp -rT $(BOOT_ISO_ORIG)-fs $(BOOT_ISO)-fs
	sudo chmod a+rw $(BOOT_ISO)-fs -R
	# Unmount the original
	sudo umount $(BOOT_ISO_ORIG)-fs
	rmdir $(BOOT_ISO_ORIG)-fs
	# Edit kernel boot parameter in "txt.cfg"
	# (append "file=/preseed.cfg" to the kernel boot parameter)
	cat $(BOOT_ISO)-fs/txt.cfg \
		| sed -e 's/initrd.gz --/initrd.gz file=\/preseed.cfg auto=true --/' \
		| sed -e 's/timeout 0/timeout 1/' \
		> $(BOOT_ISO)-fs/new-txt.cfg
	mv -f $(BOOT_ISO)-fs/new-txt.cfg $(BOOT_ISO)-fs/txt.cfg
	# Edit prompt parameter in "prompt.cfg" to disable prompt
	cat $(BOOT_ISO)-fs/prompt.cfg \
		| sed -e 's/prompt 1/prompt 0/' \
		| sed -e 's/timeout 0/timeout 1/' \
		> $(BOOT_ISO)-fs/new-prompt.cfg
	mv -f $(BOOT_ISO)-fs/new-prompt.cfg $(BOOT_ISO)-fs/prompt.cfg
	touch $@

$(BOOT_ISO).initrd.done: $(BOOT_ISO)-fs.done preseed.cfg
	sudo rm -rf $(BOOT_ISO)-fs/initrd
	mkdir -p $(BOOT_ISO)-fs/initrd
	cd $(BOOT_ISO)-fs/initrd \
		&& cat ../initrd.gz | gzip -d - | sudo cpio -id \
		&& cp ../../preseed.cfg . \
		&& find . | cpio --create --format=newc | gzip - > ../initrd.gz
	sudo rm -rf $(BOOT_ISO)-fs/initrd
	touch $@

$(BOOT_ISO).done: $(BOOT_ISO).initrd.done
	genisoimage \
		-D \
		-r \
		-V "Overview" \
		-J \
		-l \
		-b isolinux.bin \
		-c boot.cat \
		-no-emul-boot \
		-boot-load-size 4 \
		-boot-info-table \
		-o $(BOOT_ISO) \
		$(BOOT_ISO)-fs
	touch $@

dist.zip: ../../dist/overview-server-1.0-SNAPSHOT.zip
	cp $^ $@

vm-files.tar.gz: $(find vm-files/)
	rm -f $@
	tar zcvf $@ vm-files

$(MACHINE_NAME).copy-files.done: $(MACHINE_NAME).ubuntu.done dist.zip vm-files.tar.gz vm-setup.sh
	# Start the VM, if it isn't running
	$(VBOXMANAGE) startvm $(MACHINE_NAME) || true
	util/wait_for_is_ready.sh $(NETCAT) $(LOCAL_PORT_SSH)
	# Copy dist.zip
	$(VBOXMANAGE) guestcontrol $(MACHINE_NAME) cp \
		`pwd`/dist.zip \
		/home/overview/dist.zip \
		--username overview \
		--password overview
	# Copy vm-files.tar.gz
	$(VBOXMANAGE) guestcontrol $(MACHINE_NAME) cp \
		`pwd`/vm-files.tar.gz \
		/home/overview/vm-files.tar.gz \
		--username overview \
		--password overview
	# Copy guest-setup.sh
	$(VBOXMANAGE) guestcontrol $(MACHINE_NAME) cp \
		`pwd`/vm-setup.sh \
		/home/overview/vm-setup.sh \
		--username overview \
		--password overview
	# Execute the script
	$(VBOXMANAGE) guestcontrol $(MACHINE_NAME) exec \
		--image /bin/sh /home/overview/vm-setup.sh \
		--username overview \
		--password overview \
		--wait-exit \
		-- /home/overview/vm-setup.sh
	# The script should shut down the machine. Wait for that to happen.
	util/wait_for_is_off.sh $(VBOXMANAGE) $(MACHINE_NAME)
	touch $@

$(MACHINE_NAME).ova: $(MACHINE_NAME).copy-files.done
	# Stop the VM, if it's running
	$(VBOXMANAGE) controlvm $(MACHINE_NAME) poweroff || true
	util/wait_for_is_off.sh $(VBOXMANAGE) $(MACHINE_NAME)
	# Compact the disk image
	$(VBOXMANAGE) modifyhd $(IMAGE_FILENAME) --compact
	# Export the OVA
	$(VBOXMANAGE) export $(MACHINE_NAME) \
		--output $(MACHINE_NAME)-incomplete.ova \
		--manifest \
		--vsys 0 \
		--product "Overview Personal virtual server" \
		--producturl "http://www.overviewproject.org" \
		--vendor "The Overview Project" \
		--vendorurl "http://www.overviewproject.org" \
		--version "$(MACHINE_NAME)"
	# Rename
	mv $(MACHINE_NAME)-incomplete.ova $(MACHINE_NAME).ova

clean:
	$(VBOXMANAGE) list vms | grep $(MACHINE_NAME) && $(VBOXMANAGE) unregistervm $(MACHINE_NAME) || true
	rm -rf *.done *.zip *.tar.gz $(MACHINE_NAME) $(BOOT_ISO) $(BOOT_ISO)-fs *.vdi *.ova
