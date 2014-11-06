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

Depending on what version of Windows you have, you may need to hunt for this next step. You need to find a "Git Bash" icon and start up the Git Bash terminal window. When you have the Git bash terminal window, type `boot2docker ip` at the `$` prompt. ***This is the IP address where your local Overview server will listen for browsers, so write this down!*** 

![](https://raw.githubusercontent.com/znmeb/overview-server/master/znmeb-release/WindowsScreenshots/2014-11-05%2016_18_42-MINGW32__c_Users_Ed.png)


- - -

![](https://raw.githubusercontent.com/znmeb/overview-server/master/znmeb-release/WindowsScreenshots/2014-11-05%2016_08_13-Boot2Docker%20Start.png)

- - -

![](https://raw.githubusercontent.com/znmeb/overview-server/master/znmeb-release/WindowsScreenshots/2014-11-05%2016_16_35-Boot2Docker%20Start.png)

- - -

![](https://raw.githubusercontent.com/znmeb/overview-server/master/znmeb-release/WindowsScreenshots/2014-11-05%2016_25_30-Boot2Docker%20Start.png)

- - -

![](https://raw.githubusercontent.com/znmeb/overview-server/master/znmeb-release/WindowsScreenshots/2014-11-05%2016_27_01-Boot2Docker%20Start.png)
