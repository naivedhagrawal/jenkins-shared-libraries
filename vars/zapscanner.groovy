def call() {    
    pipeline {
        agent none
        parameters {
        string(name: 'targetURL', description: 'Target URL for DAST scan')
        string(name: 'ScanType', description: 'API Scan
                                                Scans APIs using OpenAPI, SOAP, or GraphQL definitions
                                                Active

                                                Basline Scan
                                                Passive scan without attacking the application
                                                Passive

                                                Full Scan
                                                Full scan including active attacks
                                                Passive + Active')
        }
        environment {
            ZAP_REPORT = 'zap-out.json'
            ZAP_SARIF = 'zap_report.sarif'
            TARGET_URL = "${params.targetURL}"
            SCAN_TYPE = "${params.ScanType}"
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
                            if (params.targetURL.trim() == '') {
                            error('Target URL cannot be empty.') }
                            sh '$SCAN_TYPE -t $TARGET_URL -J $ZAP_REPORT -l WARN -I'
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
