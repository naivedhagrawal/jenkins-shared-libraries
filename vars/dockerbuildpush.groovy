
/* @Library('k8s-shared-lib') _
dockerbuildpush(
    IMAGE_NAME: 'owasp-dependency',
    IMAGE_TAG: 'latest',
    DOCKER_HUB_USERNAME: 'naivedh',  // This is your Docker Hub username
    DOCKER_CREDENTIALS: 'docker_hub_up'
)*/

def call(Map params) {
    // Extract parameters
    def IMAGE_NAME = params.IMAGE_NAME
    def IMAGE_TAG = params.IMAGE_TAG
    def DOCKER_HUB_USERNAME = params.DOCKER_HUB_USERNAME
    def DOCKER_CREDENTIALS = params.DOCKER_CREDENTIALS

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
            REPORT_FILE = "${REPORT_FILE}"
        }

        stages {
            stage('Build Docker Image') {
                steps {
                    container('docker') {
                        script {
                            try {
                                echo "Building ${IMAGE_NAME}"
                                sh "docker build -t ${IMAGE_NAME}:${IMAGE_TAG} ."
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
                                sh "mkdir -p /root/.cache/trivy/db"
                                sh "trivy image --download-db-only --timeout 15m --debug"
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
