#!groovy

node('test-slave') {
  checkout scm

  env = [
    'DATABASE_PORT=9010',
    'OVERVIEW_MULTI_USER=true',
    "BLOB_STORAGE_FILE_BASE_DIRECTORY='${pwd()}/blob-storage'",
    "OV_SEARCH_DIRECTORY='${pwd()}/search'"
  ]

  stage('Download dependencies') {
    sh 'docker-compose pull'
    sh 'auto/setup-coffee-tests.sh'
    sh 'auto/setup-integration-tests.sh'
    sh '(cd "${pwd()}/web" && npm install)'
    sh './sbt "; set every logLevel := Level.Warn; common/update; worker/update; web/update; db-evolution-applier/update"'
  }

  stage('Unit tests') {
    withEnv(env) {
      sh 'auto/start-docker-dev-env.sh'
      sh 'auto/test-coffee-once.sh || true'
      sh './sbt "; test-db-evolution-applier/run; all/test" || true'
      junit 'web/test/assets/javascripts/autotest/results/**/test-results.xml'
      junit '*/target/test-reports/*.xml'
    }
  }

  stage('Build') {
    sh './build archive.zip'
  }

  stage('Integration tests') {
    withEnv(env) {
      sh 'auto/start-docker-dev-env.sh'
      sh 'unzip -o -q archive.zip'
      sh 'archive/db-evolution-applier/db-evolution-applier'
      sh '(trap \'kill $(jobs -p)\' EXIT; archive/worker/worker & archive/web/web -Dpidfile.path=/dev/null & curl --retry-connrefused --retry 99999 --output /dev/null --silent http://localhost:9000 && sleep 5 && auto/test-integration.sh)'
    }
  }

  stage('Publish') {
    if (currentBuild.result == null || currentBuild.result == 'SUCCESS') {
      sh 'aws s3 cp archive.zip s3://overview-builds/$(git rev-parse HEAD).zip'
    }
  }
}
