pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Backend Tests') {
            agent {
                docker {
                    image 'gradle:8.14.4-jdk21-alpine'
                }
            }
            steps {
                dir('backend') {
                    sh 'chmod +x gradlew'
                    sh './gradlew --no-daemon clean test bootJar'
                }
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: 'backend/build/test-results/test/*.xml'
                }
            }
        }

        stage('Frontend Build') {
            agent {
                docker {
                    image 'node:22-alpine'
                }
            }
            steps {
                dir('frontend') {
                    sh 'npm ci'
                    sh 'npm run build'
                }
            }
        }
    }

    post {
        success {
            echo 'Backend tests and frontend build passed. Render deploys from the merged Git branch.'
        }
        failure {
            echo 'Build failed. Review the Jenkins console and test reports.'
        }
    }
}
