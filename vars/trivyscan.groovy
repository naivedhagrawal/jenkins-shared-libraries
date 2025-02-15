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
                        if (params.git_URL && !params.git_URL.startsWith('https://')) {
                            error('git_URL must start with https://')
                        }
                    }
                }
            }

            stage('SCANNING USING TRIVY') {
                steps {
                    container('trivy') {
                        script {
                            def trivy_report_table = 'trivy-report.txt'
                            def trivy_report_json = 'trivy-report.json'
                            def imageToScan = params.image_name

                            if (params.registry_type == 'private') {
                                sh "echo '${params.docker_password}' | docker login -u '${params.docker_username}' --password-stdin"
                                imageToScan = "${params.docker_username}/${params.image_name}"
                            }

                            if (params.image_name) {
                                sh "trivy image ${imageToScan} -f table -o ${trivy_report_table} --debug"
                                sh "trivy image ${imageToScan} -f json -o ${trivy_report_json} --debug"
                            } else {
                                sh "trivy repository ${params.git_URL} -f table -o ${trivy_report_table} --debug"
                                sh "trivy repository ${params.git_URL} -f json -o ${trivy_report_json} --debug"
                            }
                            recordIssues(
                                enabledForFailure: true,
                                tool: trivy(pattern: "${trivy_report_json}", id: "trivy-json", name: "Trivy Scan Report"))
                            archiveArtifacts artifacts: trivy_report_json
                            archiveArtifacts artifacts: trivy_report_table
                        }
                    }
                }
            }
        }
    }
}
