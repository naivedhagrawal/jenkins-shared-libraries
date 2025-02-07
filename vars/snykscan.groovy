/*
snykscan(
    snykCode: true, 
    snykDep: true, 
    snykContainer: true, 
    snykIac: true, 
    snykImage: "my-custom-image:latest" // Required when snykContainer is enabled
)
*/


def call(Map params = [snykCode: true, snykDep: true, snykContainer: true, snykIac: true, snykImage: ""]) {
    pipeline {
        agent {
            kubernetes {
                yaml pod('snyk', 'snyk/snyk-cli:latest')
                showRawYaml false
            }
        }

        environment {
            SNYK_REPORTS_DIR = "snyk-reports"
        }

        stages {
            stage('Validate Parameters') {
                steps {
                    script {
                        if (params.snykContainer && (!params.snykImage || params.snykImage.trim() == "")) {
                            error("snykImage is required when snykContainer is enabled. Please provide a valid image name.")
                        }
                    }
                }
            }

            stage('Snyk Authentication') {
                steps {
                    script {
                        container('snyk') {
                            withCredentials([string(credentialsId: 'SNYK_TOKEN', variable: 'SNYK_TOKEN')]) {
                                sh """
                                    #!/bin/bash
                                    echo "Authenticating Snyk..."
                                    snyk auth ${SNYK_TOKEN}
                                    mkdir -p ${SNYK_REPORTS_DIR}
                                """
                            }
                        }
                    }
                }
            }

            stage('Snyk Code Scan') {
                when { expression { params.snykCode } }
                steps {
                    script {
                        container('snyk') {
                            checkout scm
                            sh """
                                #!/bin/bash
                                echo "Running Snyk Code Scan..."
                                snyk code test --sarif > ${SNYK_REPORTS_DIR}/snyk-code.sarif || true
                            """
                            archiveArtifacts artifacts: "${SNYK_REPORTS_DIR}/snyk-code.sarif"
                            recordIssues(
                                enabledForFailure: true,
                                tool: sarif(pattern: "${SNYK_REPORTS_DIR}/snyk-code.sarif", id: "snyk-code", name: "Snyk Code Report")
                            )
                        }
                    }
                }
            }
        }
    }
}
