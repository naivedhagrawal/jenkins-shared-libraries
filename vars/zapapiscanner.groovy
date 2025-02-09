def call() {
    pipeline {
        agent none
        parameters {
            string(name: 'apiJsonUrl', description: 'URL for the API definition JSON file', defaultValue: '')
            choice(name: 'apiFormat', choices: ['openapi', 'soap', 'graphql'], description: 'Format of the API definition')
        }
        environment {
            ZAP_REPORT = 'zap-out.json'
            ZAP_REPORT_HTML = 'zap-out.html'
            ZAP_REPORT_MD = 'zap-out.md'
            ZAP_SARIF = 'zap_report.sarif'
            API_JSON_URL = "${params.apiJsonUrl?.trim()}"
            API_FORMAT = "${params.apiFormat?.trim()}"
        }

        stages {
            stage('DAST Scanning with OWASP-ZAP') {
                agent {
                    kubernetes {
                        yaml zap()
                        showRawYaml false
                    }
                }
                steps {
                    container('zap') {
                        script {
                            if (!API_JSON_URL || API_JSON_URL.trim() == '') {
                                error('ERROR: URL for API definition JSON file cannot be empty.')
                            }
                            if (!API_FORMAT || API_FORMAT.trim() == '') {
                                error('ERROR: API format must be specified (openapi, soap, graphql).')
                            }
                            
                            // Download API JSON file with original name
                            sh "curl -O '${API_JSON_URL}'"
                            
                            // Find the downloaded JSON file
                            def apiJsonFile = sh(script: "ls *.json | head -n 1", returnStdout: true).trim()
                            if (!apiJsonFile) {
                                error('ERROR: No JSON file found after download.')
                            }
                            
                            // Run ZAP API scan directly using the JSON file URL
                            sh "zap-api-scan.py -t '${API_JSON_URL}' -f '${API_FORMAT}' -J '$ZAP_REPORT' -r '$ZAP_REPORT_HTML' -w '$ZAP_REPORT_MD' -I"
                            sh 'mv /zap/wrk/${ZAP_REPORT} .' 
                            sh 'mv /zap/wrk/${ZAP_REPORT_HTML} .'
                            sh 'mv /zap/wrk/${ZAP_REPORT_MD} .'
                        }
                    }
                    
                    archiveArtifacts artifacts: "${ZAP_REPORT}"
                    archiveArtifacts artifacts: "${ZAP_REPORT_HTML}"
                    archiveArtifacts artifacts: "${ZAP_REPORT_MD}"
                    archiveArtifacts artifacts: '*.json'  // Archive the downloaded API JSON file
                    
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
