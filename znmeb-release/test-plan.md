1. Testing on Fedora 21 is in progress. Once the construction process is done I can run Overview-specific tests.
1. I will verify that the finished znmeb/overview-release image can run on Windows 8.1 with Boot2Docker. If I get some spare time I will attempt to run the build process that way as well; it should work with possibly a few changes.
1. Someone with a Mac should test znmeb/overview-release on it.
1. Someone who knows Overview a lot better than I do should run more extensive tests. If any bugs show up between now and the hackathon, check the fixes into the source and I will rerun the builds. Note that at present the build takes about two or three hours including the time to push images up to Docker Hub.
1. In theory you do not need an account on Docker Hub to download the images. Docker should find them and grab them automatically if you type their names right.
