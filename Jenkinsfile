#!groovy

def notifySlack(String buildStatus) {
  def color
  if (buildStatus == 'STARTED') {
    color = '#D4DADF'
  } else if (buildStatus == 'SUCCESS') {
    color = 'good'
  } else {
    color = 'danger'
  }

  def msg = "${buildStatus}: ${env.JOB_NAME} #${env.BUILD_NUMBER}: ${env.BUILD_URL}"

  slackSend(color: color, message: msg)
}

node('test-slave') {
  try {
    notifySlack('STARTED')

    checkout scm

    def env = [
      'DATABASE_PORT=9010',
      'OVERVIEW_MULTI_USER=true',
      "BLOB_STORAGE_FILE_BASE_DIRECTORY=${pwd()}/blob-storage",
      "OV_SEARCH_DIRECTORY=${pwd()}/search"
    ]

    stage('Download dependencies') {
      sh 'docker-compose pull'
      sh 'auto/setup-coffee-tests.sh'
      sh 'auto/setup-integration-tests.sh'
      sh 'cd web && npm install'
      sh './sbt -Dsbt.log.noformat=true "; set every logLevel := Level.Warn; common/update; worker/update; web/update; db-evolution-applier/update"'
    }

    stage('Unit tests') {
      withEnv(env) {
        sh 'auto/start-docker-dev-env.sh'
        sh 'auto/test-coffee-once.sh || true'
        sh './sbt -Dsbt.log.noformat=true "; test-db-evolution-applier/run; all/test" || true'
        junit 'web/test/assets/javascripts/autotest/results/**/test-results.xml'
        junit '*/target/test-reports/*.xml'
      }
    }

    stage('Build') {
      sh './build archive.zip'
    }

    stage('Integration tests') {
      withEnv(env) {
        sh 'unzip -o -q archive.zip'
        sh 'auto/run-integration-tests-in-dev-env.sh'
        junit 'web/test/integration/test-results.xml'
      }
    }

    stage('Publish') {
      if (currentBuild.result == null || currentBuild.result == 'SUCCESS') {
        sh 'aws s3 cp archive.zip s3://overview-builds/$(git rev-parse HEAD).zip'
      }
    }
  } catch (any) {
    currentBuild.result = 'FAILURE'
    throw any // rethrow to prevent future steps from happening
  } finally {
    always {
      step([$class: 'Mailer', notifyEveryUnstableBuild: true, recipients: "adam@adamhooper.com", sendToIndividuals: true])
      notifySlack(currentBuild.result)
    }
  }
}
