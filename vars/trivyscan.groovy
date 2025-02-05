def call() {
    pipeline {
        agent {
            kubernetes {
                yaml pod('trivy', 'aquasec/trivy:latest')
                showRawYaml false
            }
        }
        parameters {
            string(name: 'git_URL', description: 'URL for Git Repo scan')
            string(name: 'image_name', description: 'Docker image name for scan')
        }
            
            stage('SCANNING USING TRIVY') {
                steps {
                    container('trivy') {
                        script {
                            switch (params.scanType) {
                                case 'git_URL':
                                    sh ""
                                    break
                                case 'image_name':
                                    sh "trivy image"
                                    sh "trivy repository"
                                    sh "trivy convert --input report.json --output sarif > report.sarif"
                                    break
                            }
                        }
                        recordIssues(
                            enabledForFailure: true,
                            tool: sarif(pattern: "${ZAP_SARIF}", id: "zap-SARIF", name: "DAST Report"))
                        archiveArtifacts artifacts: "${TRIVY_REPORT}"
                        archiveArtifacts artifacts: "${TRIVY_REPORT_HTML}"
                    }
                }
            }
        }
    }
