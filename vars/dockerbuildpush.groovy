/* @Library('k8s-shared-lib') _
myPipeline(
    IMAGE_NAME: 'my-custom-image',
    IMAGE_TAG: 'v1.0',
    DOCKER_HUB_USERNAME: 'mydockerhubusername',  // This is your Docker Hub username
    DOCKER_CREDENTIALS: 'docker_cred_id'
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
    def REPORT_FILE = "trivy-report.json"

    pipeline {
        agent none
        environment {
            IMAGE_NAME = IMAGE_NAME
            IMAGE_TAG = IMAGE_TAG
            DOCKER_HUB_USERNAME = DOCKER_HUB_USERNAME
            DOCKER_CREDENTIALS = DOCKER_CREDENTIALS
            REPORT_FILE = REPORT_FILE
        }

        stages {
            stage('Build Docker Image') {
                agent {
                    kubernetes {
                        yaml docker('docker-build', 'docker:latest')
                        showRawYaml false
                    }
                }
                steps {
                    container('docker-build') {
                        script {
                            try {
                                withCredentials([string(credentialsId: 'NVD_API_KEY', variable: 'NVD_API_KEY')]) {
                                    sh """
                                        docker build --build-arg NVD_API_KEY=$NVD_API_KEY -t ${IMAGE_NAME}:${IMAGE_TAG} .
                                    """

                                    // Verify image exists
                                    sh "docker images | grep '${IMAGE_NAME}' || { echo 'Docker image not found!'; exit 1; }"

                                    // Save and compress the Docker image
                                    echo "Saving and compressing Docker image..."
                                    sh """
                                        docker save ${IMAGE_NAME}:${IMAGE_TAG} | gzip > "${WORKSPACE}/${IMAGE_NAME}-${IMAGE_TAG}.tar.gz"
                                    """

                                    // Verify the compressed file
                                    sh """
                                        ls -lh "${WORKSPACE}/${IMAGE_NAME}-${IMAGE_TAG}.tar.gz"
                                        gunzip -t "${WORKSPACE}/${IMAGE_NAME}-${IMAGE_TAG}.tar.gz" || { echo 'Compressed file is corrupt!'; exit 1; }
                                    """

                                    // Stash the Docker image
                                    stash name: 'docker-image', includes: "${IMAGE_NAME}-${IMAGE_TAG}.tar.gz"
                                }
                            } catch (Exception e) {
                                error "Build Docker Image failed: ${e.getMessage()}"
                            }
                        }
                    }
                }
            }

            stage('Trivy Scan') {
                agent {
                    kubernetes {
                        yaml trivy()
                        showRawYaml false
                    }
                }
                steps {
                    container('docker') {
                        script {
                            try {
                                // Unstash the Docker image
                                unstash 'docker-image'

                                // Decompress and load the Docker image
                                sh "gunzip ${WORKSPACE}/${IMAGE_NAME}-${IMAGE_TAG}.tar.gz"
                                sh "docker load -i ${WORKSPACE}/${IMAGE_NAME}-${IMAGE_TAG}.tar"
                            } catch (Exception e) {
                                error "Failed to load Docker image: ${e.getMessage()}"
                            }
                        }
                    }
                    container('trivy') {
                        script {
                            try {
                                // Scan the Docker image with Trivy
                                sh "mkdir -p /root/.cache/trivy/db"
                                sh "trivy image --download-db-only --timeout 60m --debug"
                                echo "Scanning image with Trivy..."
                                sh "trivy image ${IMAGE_NAME}:${IMAGE_TAG} --timeout 30m --format json --output ${REPORT_FILE} --debug"
                                archiveArtifacts artifacts: "${REPORT_FILE}", fingerprint: true
                            } catch (Exception e) {
                                error "Trivy scan failed: ${e.getMessage()}"
                            }
                        }
                    }
                }
            }

            stage('Push Docker Image') {
                agent {
                    kubernetes {
                        yaml docker('docker-push', 'docker:latest')
                        showRawYaml false
                    }
                }
                steps {
                    container('docker-push') {
                        script {
                            try {
                                // Unstash the Docker image
                                unstash 'docker-image'

                                // Decompress and load the Docker image
                                sh "gunzip ${WORKSPACE}/${IMAGE_NAME}-${IMAGE_TAG}.tar.gz"
                                sh "docker load -i ${WORKSPACE}/${IMAGE_NAME}-${IMAGE_TAG}.tar"

                                // Push the Docker image to Docker Hub using the username and image tag
                                withCredentials([usernamePassword(credentialsId: "${DOCKER_CREDENTIALS}", usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
                                    echo "Logging into Docker Hub..."
                                    sh '''
                                        echo $PASSWORD | docker login -u $USERNAME --password-stdin
                                        docker tag ${IMAGE_NAME}:${IMAGE_TAG} ${DOCKER_HUB_USERNAME}/${IMAGE_NAME}:${IMAGE_TAG}
                                        docker push ${DOCKER_HUB_USERNAME}/${IMAGE_NAME}:${IMAGE_TAG}
                                    '''
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
