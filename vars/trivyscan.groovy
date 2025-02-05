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
        
        stages {
            stage('VALIDATION') {
                steps {
                    script {
                        if (!params.image_name && !params.git_URL) {
                            error('Either image_name or git_URL parameter must be provided')
                        }
                    }
                }
            }

            stage('SCANNING USING TRIVY') {
                steps {
                    container('trivy') {
                        script {
                            def trivy_report = 'trivy-report.json'
                            def trivy_report_sarif = 'trivy-report.sarif'
                            
                            if (params.image_name) {
                                sh "trivy image ${params.image_name} -f json -o ${trivy_report}"
                                sh "trivy convert --format sarif --output ${trivy_report_sarif} ${trivy_report}"
                            } else {
                                sh "trivy repository ${params.git_URL} -f json -o ${trivy_report}"
                                sh "trivy convert --format sarif --output ${trivy_report_sarif} ${trivy_report}"
                            }
                            
                            archiveArtifacts artifacts: trivy_report
                            archiveArtifacts artifacts: trivy_report_html
                        }
                    }
                }
            }
        }
    }
}
