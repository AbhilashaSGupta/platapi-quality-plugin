pipeline {
    agent any

    environment {
      K8S_CRED = credentials('effort_service_account')
      CI_PULL_CRED = credentials('artifactory_ci_ro_api-customers')
      ARTIFACTORY_PUSH_CRED = credentials('effort_artifactory_user')
      ARTIFACTORY_PLATFORM_PUSH_CRED = credentials('platform_effort_artifactory_user')
    }

    stages {
      stage('Prepare environment') {
        steps {
          script {
            gitEnv()
            env.GIT_COMMIT_SHORT = sh(returnStdout: true, script: 'git rev-parse --short HEAD').trim()

            if (env.BRANCH_NAME == 'master') {
              env.IMAGE_TAG = "MASTER_${env.BUILD_NUMBER}_${env.GIT_COMMIT_SHORT}"
            } else {
              env.IMAGE_TAG = "${env.GIT_BRANCH}_build_${env.BUILD_NUMBER}"
            }
          }
          echo "IMAGE_TAG: ${env.IMAGE_TAG}"
          sh 'printf $IMAGE_TAG > build-number.file'
        }
      }

      stage('Set up Java Environment') {
        steps {
          script {
            echo "Setting java version for the build"
            if (javaVersion == "8") {
              echo "Setting version to 1.8 for this build"
              env.JAVA_HOME = "${tool 'JDK-8'}"
            } else {
              echo "Setting version to 1.11 for this build"
              env.JAVA_HOME = "${tool 'JDK-11'}"
            }
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
