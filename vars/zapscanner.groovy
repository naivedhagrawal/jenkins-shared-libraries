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
            choices: ['full-scan','baseline','api-scan']
            )
            file(name: 'apiDefinition', description: 'API definition file for API scanning (OpenAPI/SOAP/GraphQL)')
        }
        environment {
            ZAP_REPORT = 'zap-out.json'
            ZAP_SARIF = 'zap_report.sarif'
            TARGET_URL = "${params.target_URL}"
            API_FILE_PATH = "${params.apiDefinition}"
        }

        stages {
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
                            if (params.target_URL.trim() == '') {
                                error('Target URL cannot be empty.')
                            }
                            if (params.scanType.trim() == 'api-scan' && params.apiDefinition == null) {
                                error('API definition file is required for API scan.')
                            }
                            if (params.scanType.trim() == 'full-scan') {
                                sh 'zap-full-scan.py -t "${params.target_URL}" -J $ZAP_REPORT -l WARN -I'
                            }
                            if (params.scanType.trim() == 'baseline') {
                                sh 'zap-baseline.py -t $TARGET_URL -J $ZAP_REPORT -l WARN -I'
                            }
                            if (params.scanType.trim() == 'api-scan') {
                                sh 'zap-api-scan.py -t $API_FILE_PATH -J $ZAP_REPORT -l WARN -I'
                            }
                            sh 'mv /zap/wrk/${ZAP_REPORT} .'
                        }
                        archiveArtifacts artifacts: "${env.ZAP_REPORT}"
                    }

                    container('python') {
                        script {
                            def jsonToSarif = libraryResource('zap_json_to_sarif.py')
                            writeFile file: 'zap_json_to_sarif.py', text: jsonToSarif
                            sh 'python3 zap_json_to_sarif.py'
                        }
                        archiveArtifacts artifacts: "${env.ZAP_SARIF}"
                        recordIssues(
                            enabledForFailure: true,
                            tool: sarif(pattern: "${env.ZAP_SARIF}", id: "zap-SARIF", name: "DAST Report")
                        )
                    }
                }
            }
        }
    }
}
