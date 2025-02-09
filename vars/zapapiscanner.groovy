def call() {
    pipeline {
        agent none
        parameters {
            string(name: 'apiGitUrl', description: 'Git URL for the API definition')
            choice(name: 'apiFormat', choices: ['openapi', 'soap', 'graphql'], description: 'Format of the API definition')
        }
        environment {
            ZAP_REPORT = 'zap-out.json'
            ZAP_REPORT_HTML = 'zap-out.html'
            ZAP_REPORT_MD = 'zap-out.md'
            ZAP_SARIF = 'zap_report.sarif'
            API_GIT_URL = "${params.apiGitUrl?.trim()}"
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
                        if (API_GIT_URL == null || API_GIT_URL.trim() == '') {
                            error('ERROR: Git URL for API definition cannot be empty.')
                        }
                        if (API_FORMAT == null || API_FORMAT.trim() == '') {
                            error('ERROR: API format must be specified (openapi, soap, graphql).')
                        }
                        
                        // Clone the API repository
                        sh "git clone ${API_GIT_URL} api-definitions"
                        
                        // Find API definition file (specifically api.json in root directory)
                        def API_FILE = 'api-definitions/api.json'
                        
                        if (!fileExists(API_FILE)) {
                            error("ERROR: No valid api.json file found in the root directory of the repository.")
                        }
                        
                        echo "Found API definition file: ${API_FILE}"
                        writeFile file: 'api_git_url.txt', text: "API Git URL: ${API_GIT_URL}"
                        
                        // Run ZAP API scan
                        sh "zap-api-scan.py -t '${API_FILE}' -f '${API_FORMAT}' -J '$ZAP_REPORT' -r '$ZAP_REPORT_HTML' -w '$ZAP_REPORT_MD' -I"
                        sh 'mv /zap/wrk/${ZAP_REPORT} .' 
                        sh 'mv /zap/wrk/${ZAP_REPORT_HTML} .'
                        sh 'mv /zap/wrk/${ZAP_REPORT_MD} .'
                    }
                    
                    archiveArtifacts artifacts: "${ZAP_REPORT}"
                    archiveArtifacts artifacts: "${ZAP_REPORT_HTML}"
                    archiveArtifacts artifacts: "${ZAP_REPORT_MD}"
                    archiveArtifacts artifacts: 'api_git_url.txt'  // Archive the Git URL details
                    
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
}
