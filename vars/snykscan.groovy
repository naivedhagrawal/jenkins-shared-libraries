def call() {
    pipeline {
        agent none
        parameters {
            string(name: 'gitUrl', description: 'Git repository URL')
            string(name: 'imageName', description: 'Container image name (required for container scan)', defaultValue: '')
            extendedChoice(
                name: 'scanTypes',
                description: '''Choose scan types (multi-select):
SCA - Software Composition Analysis
SAST - Static Application Security Testing
Container - Container Security Scan
IaC - Infrastructure as Code Scan''',
                multiSelectDelimiter: ',',
                type: 'PT_CHECKBOX',
                value: 'sca,sast,container,iac'
            )
        }

        stages {
            stage('Validate Parameters') {
                steps {
                    script {
                        def selectedScans = params.scanTypes.split(',')
                        if (selectedScans.contains('container') && (!params.imageName || params.imageName.trim() == '')) {
                            error('ERROR: Image name is required for container scan.')
                        }
                    }
                }
            }
            
            stage('Snyk Scanning') {
                gent {
                    kubernetes {
                        yaml pod('snyk', 'snyk/snyk-cli:latest')
                        showRawYaml false
                    }
                }
                steps {
                    container('snyk') {
                    script {
                        def selectedScans = params.scanTypes.split(',')
                        if (selectedScans.contains('sca')) {
                            sh "snyk test --all-projects --sarif > snyk-sca.sarif"
                            recordIssues(
                                enabledForFailure: true,
                                tool: sarif(pattern: "snyk-sca.sarif", id: "sca-SARIF", name: "SCA Report")
                            )
                        }
                        if (selectedScans.contains('sast')) {
                            sh "snyk code test --sarif > snyk-sast.sarif"
                            recordIssues(
                                enabledForFailure: true,
                                tool: sarif(pattern: "snyk-sast.sarif", id: "sast-SARIF", name: "SAST Report")
                            )
                        }
                        if (selectedScans.contains('container')) {
                            sh "snyk container test ${params.imageName} --sarif > snyk-container.sarif"
                            recordIssues(
                                enabledForFailure: true,
                                tool: sarif(pattern: "snyk-container.sarif", id: "container-SARIF", name: "Container Security Report")
                            )
                        }
                        if (selectedScans.contains('iac')) {
                            sh "snyk iac test --sarif > snyk-iac.sarif"
                            recordIssues(
                                enabledForFailure: true,
                                tool: sarif(pattern: "snyk-iac.sarif", id: "iac-SARIF", name: "IaC Security Report")
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
