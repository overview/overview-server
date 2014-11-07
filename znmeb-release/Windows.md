# Running Overview Server with Windows Boot2Docker

## Installing Boot2Docker

1. Go to <http://boot2docker.io/> and download the Boot2Docker Windows installer.
2. Run the installer. You will see the following screens:

- - -

![](https://raw.githubusercontent.com/znmeb/overview-server/master/znmeb-release/WindowsScreenshots/2014-11-05%2015_56_25-Setup%20-%20Boot2Docker%20for%20Windows.png)<br><br>
Press 'Next'.

- - -

![](https://raw.githubusercontent.com/znmeb/overview-server/master/znmeb-release/WindowsScreenshots/2014-11-05%2015_56_45-Setup%20-%20Boot2Docker%20for%20Windows.png)<br><br>
Press 'Next'.

- - -

![](https://raw.githubusercontent.com/znmeb/overview-server/master/znmeb-release/WindowsScreenshots/2014-11-05%2015_57_10-Setup%20-%20Boot2Docker%20for%20Windows.png)<br><br>
Make sure all three options are checked and press 'Next'.

- - -

![](https://raw.githubusercontent.com/znmeb/overview-server/master/znmeb-release/WindowsScreenshots/2014-11-05%2015_57_31-Setup%20-%20Boot2Docker%20for%20Windows.png)<br><br>
Press 'Next'.

- - -

![](https://raw.githubusercontent.com/znmeb/overview-server/master/znmeb-release/WindowsScreenshots/2014-11-05%2015_57_57-Setup%20-%20Boot2Docker%20for%20Windows.png)<br><br>
Make sure all three options are checked and press 'Next'.

- - -

![](https://raw.githubusercontent.com/znmeb/overview-server/master/znmeb-release/WindowsScreenshots/2014-11-05%2015_58_17-Setup%20-%20Boot2Docker%20for%20Windows.png)<br><br>
Press 'Install'. Installation will proceed.

- - -

![](https://raw.githubusercontent.com/znmeb/overview-server/master/znmeb-release/WindowsScreenshots/2014-11-05%2015_59_51-Setup%20-%20Boot2Docker%20for%20Windows.png)<br><br>
Press 'Finish'. Your system will reboot.

## Starting Boot2Docker

Find the Boot2Docker icon and start it. It will take some time the first time you do this, but eventually you wll get this screen. This is the console of the Docker host.

![](https://raw.githubusercontent.com/znmeb/overview-server/master/znmeb-release/WindowsScreenshots/2014-11-05%2016_07_16-Boot2Docker%20Start.png)

## Finding the IP address of the server

Depending on what version of Windows you have, you may need to hunt for this next step. You need to find a "Git Bash" icon and start up the Git Bash terminal window. When you have the Git Bash terminal window, type `boot2docker ip` at the `$` prompt. ***This is the IP address where your local Overview server will listen for browsers, so write this down!*** After you've written down the IP address, close the window.

![](https://raw.githubusercontent.com/znmeb/overview-server/master/znmeb-release/WindowsScreenshots/2014-11-05%2016_18_42-MINGW32__c_Users_Ed.png)

## Downloading the Overview Server image

Go back to the Boot2Docker console and type `docker pull znmeb/overview-stripped`. This will take some time depending on your bandwidth, but you only have to do it once.

![](https://raw.githubusercontent.com/znmeb/overview-server/master/znmeb-release/WindowsScreenshots/2014-11-05%2016_08_13-Boot2Docker%20Start.png)

When it completes, type `docker images` and you will see that it has been downloaded. You can type `docker images` at any time to list the images you have.

![](https://raw.githubusercontent.com/znmeb/overview-server/master/znmeb-release/WindowsScreenshots/2014-11-05%2016_16_35-Boot2Docker%20Start.png)

## Running the server

There are two types of objects in Docker, *images* and *containers*. You've seen an image, which has the name `znmeb/overview-stripped`. An image is like a file or a document - it contains information but it can't do anything. This particular image has all the information needed to run the Overview server.

A container is like a virtual machine. It uses some of your host machine's processor, RAM and disk capacity to execute the image. So to run the Overview server, you need to create a container and run the image in it. The command to do that is `docker run`, and it has a number of options. The ones we'll use are

* `-it` run interactively. You'll have a console window into the server
* `-p 9000:9000` Overview Server listens for connections on port 9000. This option maps the server's port 9000 to port 9000 on the IP address you wrote down before.
* `--name=overview-container` Containers have names, just like images. If you don't give the container a name, Docker will create one. Then you'll have to figure out what name it picked. So specify one.

After the parameters, you'll type the name of the image you want to run, in this case `znmeb/overview-stripped`. I typed the image name on a new line by using a backslash to make it easier to read, but you don't have to do that.

So the final command is `docker run -it -p 9000:9000 --name=overview-container znmeb/overview-stripped`. Type that into the console and Docker will create a container and run the image in it.

![](https://raw.githubusercontent.com/znmeb/overview-server/master/znmeb-release/WindowsScreenshots/2014-11-05%2016_25_30-Boot2Docker%20Start.png)

## Wait for the server to stabilize
The first time the server runs, it needs to set up some databases. Once that's completed, the screen will look like this:

![](https://raw.githubusercontent.com/znmeb/overview-server/master/znmeb-release/WindowsScreenshots/2014-11-05%2016_27_01-Boot2Docker%20Start.png)

When the activity stops, the server is ready. Browse to port 9000 at the IP address you wrote down, and you'll be connected to the Overview Server. You can upload documents and analyze them.

## Stopping the server

As you work with Overview in the browser, you can minimize the console window if you find it distracting. It will log all your interactions. When you're finished with a session, close the browser window or tab and go back to the console. It will probably look something like this:

![](https://raw.githubusercontent.com/znmeb/overview-server/master/znmeb-release/WindowsScreenshots/2014-11-06%2014_27_30-Boot2Docker%20Start.png)

Type a <kbd>ctrl</kbd>+<kbd>D</kbd>. This will send a signal to the server to shut down. Once the server shuts down, the Docker container will stop running. You'll be back in the *Boot2Docker* console, not the Overview Server console. It will look like this:

![](https://raw.githubusercontent.com/znmeb/overview-server/master/znmeb-release/WindowsScreenshots/2014-11-06%2014_31_49-Boot2Docker%20Start.png)

Your container is still there. All of the data you uploaded and all of the underlying databases are there. To see this, type `docker ps -a` in the Boot2Docker console.

![](https://raw.githubusercontent.com/znmeb/overview-server/master/znmeb-release/WindowsScreenshots/2014-11-06%2014_34_56-Boot2Docker%20Start.png)

As we'll see shortly, the port mapping is still there too.

## Restarting the server

Go back to the Boot2Docker console and type `docker start -ai overview-container`. 

![](https://raw.githubusercontent.com/znmeb/overview-server/master/znmeb-release/WindowsScreenshots/2014-11-06%2015_00_04-Boot2Docker%20Start.png)

When the console stabilizes, browse to port 9000 on the host-only network IP address and you'll see the Overview Server. Use <kbd>ctrl</kbd>+<kbd>D</kbd> to stop this session as before.

## Checkpointing a session as a new image

You can checkpoint a container's state to a new image for later use. Go to the Boot2Docker console and verify that your container is still there with `docker ps -a`. Then type `docker commit overview-container overview-checkpoint-image` to checkpoint the container to the image. Then type `docker images` to verify that the checkpoint has been completed.

![](https://raw.githubusercontent.com/znmeb/overview-server/master/znmeb-release/WindowsScreenshots/2014-11-06%2015_32_46-Boot2Docker%20Start.png)

## Exiting Boot2Docker

Go back to the Boot2Docker console and type `exit` and <kbd>enter</kbd>.

