/* @Library('k8s-shared-lib') _
myPipeline(
    IMAGE_NAME: 'my-custom-image',
    IMAGE_TAG: 'v1.0',
    DOCKER_HUB_USERNAME: 'mydockerhubusername',
    DOCKER_CREDENTIALS: 'docker_cred_id',
    API_KEY: 'your_API_KEY' // Now passed as a parameter
)*/

def call(Map params) {
    // Extract parameters
    def IMAGE_NAME = params.IMAGE_NAME
    def IMAGE_TAG = params.IMAGE_TAG
    def DOCKER_HUB_USERNAME = params.DOCKER_HUB_USERNAME
    def DOCKER_CREDENTIALS = params.DOCKER_CREDENTIALS
    def API_KEY = params.API_KEY

    if (!IMAGE_NAME || !IMAGE_TAG || !DOCKER_HUB_USERNAME || !DOCKER_CREDENTIALS) {
        error "Missing required parameters!"
    }

    // Fixed report file name
    def REPORT_FILE = "trivy-report.sarif"

    pipeline {
        agent {
            kubernetes {
                yaml trivy()
                showRawYaml false
            }
        }
        environment {
            IMAGE_NAME = "${IMAGE_NAME}"
            IMAGE_TAG = "${IMAGE_TAG}"
            DOCKER_HUB_USERNAME = "${DOCKER_HUB_USERNAME}"
            DOCKER_CREDENTIALS = "${DOCKER_CREDENTIALS}"
            API_KEY = "${API_KEY}"
            REPORT_FILE = "${REPORT_FILE}"
        }

        stages {
            stage('Build Docker Image') {
                steps {
                    container('docker') {
                        script {
                            try {
                                sh "docker build --build-arg API_KEY=${API_KEY} -t ${IMAGE_NAME}:${IMAGE_TAG} ."
                            } catch (Exception e) {
                                error "Build Docker Image failed: ${e.getMessage()}"
                            }
                        }
                    }
                }
            }

            stage('Trivy Scan') {
                steps {
                    container('trivy') {
                        script {
                            try {
                                // Scan the Docker image with Trivy
                                sh "mkdir -p /root/.cache/trivy/db"
                                sh "trivy image --download-db-only --timeout 60m --debug"
                                echo "Scanning image with Trivy..."
                                sh "trivy image ${IMAGE_NAME}:${IMAGE_TAG} --timeout 30m --format sarif --output ${REPORT_FILE} --debug"
                                recordIssues(
                                    enabledForFailure: true,
                                    tool: sarif(pattern: "${env.REPORT_FILE}", id: "TRIVY-SARIF", name: "Trivy-Report" ))
                                archiveArtifacts artifacts: "${REPORT_FILE}", fingerprint: true
                            } catch (Exception e) {
                                error "Trivy scan failed: ${e.getMessage()}"
                            }
                        }
                    }
                }
            }

            stage('Push Docker Image') {
                steps {
                    container('docker') {
                        script {
                            try {
                                withCredentials([usernamePassword(credentialsId: DOCKER_CREDENTIALS, usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
                                    echo "Logging into Docker Hub..."
                                    sh """
                                        echo \$PASSWORD | docker login -u \$USERNAME --password-stdin
                                        docker tag ${IMAGE_NAME}:${IMAGE_TAG} ${DOCKER_HUB_USERNAME}/${IMAGE_NAME}:${IMAGE_TAG}
                                        docker push ${DOCKER_HUB_USERNAME}/${IMAGE_NAME}:${IMAGE_TAG}
                                    """
                                }
                            } catch (Exception e) {
                                error "Push Docker Image failed: ${e.getMessage()}"
                            }
                        }
                    }
                }
            }
        }
    }
}
