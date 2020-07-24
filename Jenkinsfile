pipeline {
    stages{
        stage ('Run clean') {
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
