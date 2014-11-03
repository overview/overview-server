1. Sync the source with overview-server upstream. Push, which will trigger a rebuild of the two input images on Docker Hub.
1. Go into Dockerfiles/developer and execute run.bash. This will do the long developer build and give the image znmeb/overview-developer.
1. Go into Dockerfiles/zipfile and execute run.bash. This will put a release zipfile in Dockerfiles/release
1. Go into Dockerfiles/release and execute run.bash. This will make the release image znmeb/overview-release.
1. Go into Dockerfiles and execute push.bash. This will push the two useful images (overview-developer and overview-release) up to Docker Hub. It takes a fair amount of time so I've broken it out.
