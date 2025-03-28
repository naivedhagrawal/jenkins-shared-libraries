/* @Library('k8s-shared-lib') _
dockerbuildpush(
    IMAGE_NAME: 'owasp-dependency',
    IMAGE_TAG: 'latest',
    DOCKER_HUB_USERNAME: 'naivedh',
    DOCKER_CREDENTIALS: 'docker_hub_up',
    GITLAB_URL: 'https://gitlab.com/your-repo.git',
    GIT_BRANCH: 'main',
    GIT_CREDENTIALS: 'gitlab_cred'  // GitLab credentials ID
)*/

def call(Map params) {
    // Extract parameters
    def IMAGE_NAME = params.IMAGE_NAME
    def IMAGE_TAG = params.IMAGE_TAG
    def DOCKER_HUB_USERNAME = params.DOCKER_HUB_USERNAME
    def DOCKER_CREDENTIALS = params.DOCKER_CREDENTIALS
    def GITLAB_URL = params.GITLAB_URL
    def GIT_BRANCH = params.GIT_BRANCH
    def GIT_CREDENTIALS = params.GIT_CREDENTIALS

    if (!IMAGE_NAME || !IMAGE_TAG || !DOCKER_HUB_USERNAME || !DOCKER_CREDENTIALS || !GITLAB_URL || !GIT_BRANCH || !GIT_CREDENTIALS) {
        error "Missing required parameters!"
    }

    // Report file names
    def REPORT_FILE = "trivy-report.sarif"
    def TABLE_REPORT_FILE = "trivy-report.txt"

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
            GITLAB_URL = "${GITLAB_URL}"
            GIT_BRANCH = "${GIT_BRANCH}"
            GIT_CREDENTIALS = "${GIT_CREDENTIALS}"
            REPORT_FILE = "${REPORT_FILE}"
        }

        stages {
            stage('Full Pipeline Execution') {
                steps {
                    container('docker') {
                        script {
                            try {
                                echo "Cloning repository from ${GITLAB_URL} - Branch: ${GIT_BRANCH}"
                                
                                // Use GitLab credentials to authenticate
                                withCredentials([usernamePassword(credentialsId: GIT_CREDENTIALS, usernameVariable: 'GIT_USER', passwordVariable: 'GIT_PASS')]) {
                                    def gitUrlWithCreds = GITLAB_URL.replace('https://', "https://${GIT_USER}:${GIT_PASS}@")
                                    sh "git clone --branch ${GIT_BRANCH} ${gitUrlWithCreds} repo"
                                }

                                echo "Building Docker image..."
                                sh """
                                    cd repo
                                    docker build -t ${IMAGE_NAME}:${IMAGE_TAG} .
                                """

                                echo "Running Trivy scan..."
                                sh "if [ ! -d /root/.cache/trivy/db ]; then mkdir -p /root/.cache/trivy/db; fi"
                                sh "trivy image --download-db-only --timeout 15m --debug"
                                sh "trivy image ${IMAGE_NAME}:${IMAGE_TAG} --timeout 15m -f sarif -o ${REPORT_FILE}"
                                sh "trivy image ${IMAGE_NAME}:${IMAGE_TAG} --timeout 15m -f table -o ${TABLE_REPORT_FILE}"

                                echo "Archiving scan reports..."
                                recordIssues(
                                    enabledForFailure: true,
                                    tool: sarif(pattern: "${REPORT_FILE}", id: "trivy-sarif", name: "Image Scan Report")
                                )
                                archiveArtifacts artifacts: "${REPORT_FILE}", fingerprint: true
                                archiveArtifacts artifacts: "${TABLE_REPORT_FILE}", fingerprint: true

                                echo "Pushing Docker image to Docker Hub..."
                                withCredentials([usernamePassword(credentialsId: DOCKER_CREDENTIALS, usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
                                    sh """
                                        echo \$PASSWORD | docker login -u \$USERNAME --password-stdin
                                        docker tag ${IMAGE_NAME}:${IMAGE_TAG} ${DOCKER_HUB_USERNAME}/${IMAGE_NAME}:${IMAGE_TAG}
                                        docker push ${DOCKER_HUB_USERNAME}/${IMAGE_NAME}:${IMAGE_TAG}
                                    """
                                }
                            } catch (Exception e) {
                                error "Pipeline execution failed: ${e.getMessage()}"
                            }
                        }
                    }
                }
            }
        }
    }
}
