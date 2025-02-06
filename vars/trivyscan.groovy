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
                            def trivy_report_table = 'trivy-report.txt'
                            def trivy_report_sarif = 'trivy-report.sarif'
                            
                            if (params.image_name) {
                                sh "trivy image ${params.image_name} -f table -o ${trivy_report_table} --debug"
                                sh "trivy image ${params.image_name} -f sarif -o ${trivy_report_sarif} --debug"
                            } else {
                                sh "trivy repository ${params.git_URL} -f table -o ${trivy_report_table} --debug"
                                sh "trivy repository ${params.git_URL} -f sarif -o ${trivy_report_sarif} --debug"
                            }
                            recordIssues(
                                enabledForFailure: true,
                                tool: sarif(pattern: "${trivy_report_sarif}", id: "trivy-SARIF", name: "Trivy Scan Report"))
                            archiveArtifacts artifacts: trivy_report_sarif
                            archiveArtifacts artifacts: trivy_report_table
                        }
                    }
                }
            }
        }
    }
}
