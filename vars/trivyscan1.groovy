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
            choice(name: 'registry_type', choices: ['public', 'private'], description: 'Registry type (public or private)')
            string(name: 'docker_username', description: 'Docker Hub Username', defaultValue: '')
            password(name: 'docker_password', description: 'Docker Hub Password', defaultValue: '')
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

                            if (params.registry_type == 'private') {
                                sh "echo '${params.docker_password}' | docker login -u '${params.docker_username}' --password-stdin"
                                def imageToScan = "${params.docker_username}/${params.image_name}"
                            } else {
                                def imageToScan = params.image_name
                            }

                            if (params.image_name) {
                                sh "trivy image ${imageToScan} -f table -o ${trivy_report_table} --debug"
                                sh "trivy image ${imageToScan} -f sarif -o ${trivy_report_sarif} --debug"
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
