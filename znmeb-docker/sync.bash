#! /bin/bash -v

# sync with the Overview project!
git remote add upstream https://github.com/overview/overview-server
git remote -v
git fetch upstream
git checkout master
git merge upstream/master
git status
