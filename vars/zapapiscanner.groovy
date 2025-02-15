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
                            if (!params.apiJsonUrl?.trim()) {
                                error('ERROR: URL for API definition JSON file cannot be empty.')
                            }
                            if (!params.apiFormat?.trim()) {
                                error('ERROR: API format must be specified (openapi, soap, graphql).')
                            }
                            
                            echo "API JSON URL: ${params.apiJsonUrl}"
                            echo "API Format: ${params.apiFormat}"
                            
                            // Run ZAP API scan directly using the JSON file URL
                            sh "zap-api-scan.py -t '${params.apiJsonUrl}' -f '${params.apiFormat}' -J '$ZAP_REPORT' -r '$ZAP_REPORT_HTML' -w '$ZAP_REPORT_MD' -I"
                            sh 'mv /zap/wrk/${ZAP_REPORT} .' 
                            sh 'mv /zap/wrk/${ZAP_REPORT_HTML} .'
                            sh 'mv /zap/wrk/${ZAP_REPORT_MD} .'
                        }
                    }
                    
                    archiveArtifacts artifacts: "${ZAP_REPORT}"
                    archiveArtifacts artifacts: "${ZAP_REPORT_HTML}"
                    archiveArtifacts artifacts: "${ZAP_REPORT_MD}"
                }
            }
        }
    }
}
