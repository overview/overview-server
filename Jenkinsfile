pipeline {
  agent {
    node {
      label 'test-slave'
    }
  }

  options {
    timeout(time: 1, unit: 'HOURS')
    skipStagesAfterUnstable()
  }

  stages {
    stage('Build') {
      steps {
        sh 'docker/build'
      }
    }

    stage('Unit test') {
      steps {
        ansiColor('xterm') {
          sh 'docker/unit-test'
        }
      }
    }

    stage('Integration test') {
      steps {
        ansiColor('xterm') {
          sh 'integration-test/run-in-docker-compose'
        }
      }
    }

    stage('Publish') {
      when {
        branch 'master'
      }
      environment {
        DOCKER_HUB = credentials('docker-hub');
      }
      steps {
        sh 'DOCKER_HUB_USERNAME="$DOCKER_HUB_USR" DOCKER_HUB_PASSWORD="$DOCKER_HUB_PSW" docker/push'
      }
    }

    stage('Deploy') {
      when {
        branch 'master'
      }
      steps {
        withKubeConfig([credentialsId: 'overview-production-kubernetes', serverUrl: 'https://45EBEF84BD339E0D3D9716507CE1C450.yl4.us-east-1.eks.amazonaws.com']) {
          sh 'kubernetes/deploy'
        }
      }
    }
  }

  post {
    always {
      junit 'unit-test-results/**/*.xml'
      junit 'integration-test/reports/**/*.xml'
    }
  }
}
