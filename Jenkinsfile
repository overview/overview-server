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

    stage('Build') {
      sh 'docker/build'
    }

    stage('Unit test') {
      sh 'docker/unit-test'
      junit 'unit-test-results/**/*.xml'
    }

    stage('Integration test') {
      ansiColor('xterm') {
        sh 'integration-test/run-in-docker-compose'
      }
      junit 'integration-test/reports/**/*.xml'
    }

    stage('Publish') {
      if (currentBuild.result == null || currentBuild.result == 'SUCCESS') {
        withCredentials([usernamePassword(credentialsId: 'docker-hub', usernameVariable: 'DOCKER_HUB_USERNAME', passwordVariable: 'DOCKER_HUB_PASSWORD')]) {
          sh 'docker/push && (cd kubernetes && ./deploy)'
        }
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
