def call() {
    pipeline {
        agent none
        parameters {
            string(name: 'target_URL', description: 'Target URL for DAST scan')
            choice(
                name: 'scanType',
                description: '''Full Scan - Full scan including active attacks
Baseline Scan - Passive scan without attacking the application
API Scan - Scans APIs using OpenAPI, SOAP, or GraphQL definitions''',
                choices: ['full-scan', 'baseline', 'api-scan']
            )
            file(name: 'apiDefinition', description: 'API definition file for API scanning (OpenAPI/SOAP/GraphQL)')
        }
        environment {
            ZAP_REPORT = 'zap-out.json'
            ZAP_SARIF = 'zap_report.sarif'
            TARGET_URL = "${params.target_URL?.trim()}"
            API_FILE_PATH = "${params.apiDefinition}"
        }

        stages {
            stage('Validate Parameters') {
                steps {
                    script {
                        if (!TARGET_URL || TARGET_URL == '') {
                            error('ERROR: Target URL cannot be empty.')
                        }
                        if (params.scanType == 'api-scan' && (!API_FILE_PATH || API_FILE_PATH == '')) {
                            error('ERROR: API definition file is required for API scan.')
                        }
                    }
                }
            }
            
            stage('DAST SCANNING USING OWASP-ZAP') {
                agent {
                    kubernetes {
                        yaml zap()
                        showRawYaml false
                    }
                }
                steps {
                    container('zap') {
                        script {
                            switch (params.scanType) {
                                case 'full-scan':
                                    sh "zap-full-scan.py -t $TARGET_URL -J $ZAP_REPORT -l WARN -I"
                                    break
                                case 'baseline':
                                    sh "zap-baseline.py -t $TARGET_URL -J $ZAP_REPORT -l WARN -I"
                                    break
                                case 'api-scan':
                                    sh "zap-api-scan.py -t $TARGET_URL -f $API_FILE_PATH -J $ZAP_REPORT -l WARN -I"
                                    break
                            }
                            sh 'mv /zap/wrk/${ZAP_REPORT} .' 
                        }
                        archiveArtifacts artifacts: "${ZAP_REPORT}"
                    }
                }
            }
            
            stage('Convert and Archive Report') {
                agent {
                    kubernetes {
                        yaml python()
                        showRawYaml false
                    }
                }
                steps {
                    container('python') {
                        script {
                            def jsonToSarif = libraryResource('zap_json_to_sarif.py')
                            writeFile file: 'zap_json_to_sarif.py', text: jsonToSarif
                            sh 'python3 zap_json_to_sarif.py'
                        }
                        archiveArtifacts artifacts: "${ZAP_SARIF}"
                        recordIssues(
                            enabledForFailure: true,
                            tool: sarif(pattern: "${ZAP_SARIF}", id: "zap-SARIF", name: "DAST Report")
                        )
                    }
                }
            }
        }
    }
}
