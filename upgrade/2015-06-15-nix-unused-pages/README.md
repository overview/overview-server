Deletes Pages that no documents refer to.

How to run on production
------------------------

1. On dev machine, `./sbt upgrade-2015-06-15-nix-unused-pages/stage`
2. On dev machine, `(cd upgrade/2015-06-15-nix-unused-pages/target/universal && mv stage 2015-06-15-nix-unused-pages && zip -r ../../../../upgrade-2015-06-15-nix-unused-pages.zip 2015-06-15-nix-unused-pages && mv 2015-06-15-nix-unused-pages stage)`
3. Copy the zipfile to `production/worker` (it can write to S3)
4. On the instance, `unzip upgrade-2015-06-15-nix-unused-pages.zip`
5. On the instance, `aws s3 cp s3://overview-production-secrets/worker-env.sh env.sh && . ./env.sh; rm env.sh`
6. On the instance, `./2015-06-15-nix-unused-pages/bin/upgrade-2015-06-15-nix-unused-pages`
