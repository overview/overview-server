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

    stage('Download dependencies') {
      sh 'docker-compose build'
      sh 'docker-compose pull database redis'
    }

    stage('Build and Test') {
      sh 'auto/test-everything.sh'
      junit 'web/test/js/**/test-results.xml'
      junit 'unit-test-results/**/*.xml'
      junit 'integration-test/test-results.xml'
    }

    stage('Publish') {
      if (currentBuild.result == null || currentBuild.result == 'SUCCESS') {
        sh 'auto/publish-from-jenkins-test-slave.sh'
        currentBuild.result = 'SUCCESS'
      }
    }
  } catch (any) {
    currentBuild.result = 'FAILURE'
    throw any // rethrow to prevent future steps from happening
  } finally {
    step([$class: 'Mailer', notifyEveryUnstableBuild: true, recipients: "adam@adamhooper.com", sendToIndividuals: true])
    notifySlack(currentBuild.result)
  }
}
