pipeline {
    agent any

    environment {
      K8S_CRED = credentials('effort_service_account')
      CI_PULL_CRED = credentials('artifactory_ci_ro_api-customers')
      ARTIFACTORY_PUSH_CRED = credentials('effort_artifactory_user')
      ARTIFACTORY_PLATFORM_PUSH_CRED = credentials('platform_effort_artifactory_user')
    }

    stages {
      stage('Set up Java Environment') {
        steps {
          script {
            echo "Setting java version for the build"
            echo "Setting version to 1.11 for this build"
            env.JAVA_HOME = "${tool 'JDK-11'}"
            env.PATH = "${env.JAVA_HOME}/bin:${env.PATH}"
            sh 'java -version'
          }
        }
      }

      stage('Clean up previously generated files') {
          steps {
                script {
                    sh """
                    ./gradlew clean
                    """
                }
            }
        }
        stage ('Run build') {
            steps {
                script {
                    sh """
                    ./gradlew build
                    """
                }
            }
        }
        stage ('Run deploy') {
            when { branch 'master' }
            steps {
                script {
                    sh """
                    ./gradlew artifactoryPublish
                    """
                }
            }
        }

    }
    post {
        always {
            cleanWs()
        }
    }
}
