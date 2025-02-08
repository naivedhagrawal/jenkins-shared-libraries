def call() {
    pipeline {
        agent none
        parameters {
            string(name: 'gitUrl', description: 'Git repository URL', defaultValue: '')
            string(name: 'imageName', description: 'Container image name', defaultValue: '')
        }

        stages {
            stage('Validate Input') {
                steps {
                    script {
                        if (!params.gitUrl?.trim() && !params.imageName?.trim()) {
                            error('ERROR: Either Git repository URL or Container image name must be provided.')
                        }
                    }
                }
            }
            
            stage('Snyk Scanning') {
                agent {
                    kubernetes {
                        yaml pod('snyk', 'snyk/snyk-cli:latest')
                        showRawYaml false
                    }
                }
                steps {
                    container('snyk') {
                        script {
                            if (params.gitUrl?.trim()) {
                                sh "snyk test --all-projects --sarif > snyk-sca.sarif"
                                recordIssues(
                                    enabledForFailure: true,
                                    tool: sarif(pattern: "snyk-sca.sarif", id: "sca-SARIF", name: "SCA Report")
                                )
                                
                                sh "snyk code test --sarif > snyk-sast.sarif"
                                recordIssues(
                                    enabledForFailure: true,
                                    tool: sarif(pattern: "snyk-sast.sarif", id: "sast-SARIF", name: "SAST Report")
                                )
                                
                                sh "snyk iac test --sarif > snyk-iac.sarif"
                                recordIssues(
                                    enabledForFailure: true,
                                    tool: sarif(pattern: "snyk-iac.sarif", id: "iac-SARIF", name: "IaC Security Report")
                                )
                            }
                            
                            if (params.imageName?.trim()) {
                                sh "snyk container test ${params.imageName} --sarif > snyk-container.sarif"
                                recordIssues(
                                    enabledForFailure: true,
                                    tool: sarif(pattern: "snyk-container.sarif", id: "container-SARIF", name: "Container Security Report")
                                )
                            }
                        }
                        archiveArtifacts artifacts: "snyk-*.sarif"
                    }
                }
            }
        }
    }
}
