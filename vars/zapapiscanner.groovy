def call() {
    pipeline {
        agent none
        parameters {
            string(name: 'apiGitUrl', description: 'Git URL for the API definition (OpenAPI/SOAP/GraphQL)')
        }
        environment {
            ZAP_REPORT = 'zap-out.json'
            ZAP_REPORT_HTML = 'zap-out.html'
            ZAP_SARIF = 'zap_report.sarif'
            API_GIT_URL = "${params.apiGitUrl?.trim()}"
        }

        stages {
            stage('Validate Parameters') {
                steps {
                    script {
                        if (API_GIT_URL == null || API_GIT_URL.trim() == '') {
                            error('ERROR: Git URL for API definition cannot be empty.')
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
                            sh "git clone ${API_GIT_URL} api-definitions"
                            def API_DIR = 'api-definitions' // Directory where the API definition is cloned
                            def API_FILE = sh(script: "find ${API_DIR} -type f -name '*.yaml' -or -name '*.json'", returnStdout: true).trim()
                            
                            // Save the Git URL to a file
                            writeFile file: 'api_git_url.txt', text: "API Git URL: ${API_GIT_URL}"

                            // Perform the API scan
                            sh "zap-api-scan.py -t '${API_FILE}' -f '${API_FILE}' -J '$ZAP_REPORT' -r '$ZAP_REPORT_HTML' -I"
                            sh 'mv /zap/wrk/${ZAP_REPORT} .' 
                            sh 'mv /zap/wrk/${ZAP_REPORT_HTML} .'
                        }
                        archiveArtifacts artifacts: "${ZAP_REPORT}"
                        archiveArtifacts artifacts: "${ZAP_REPORT_HTML}"
                        archiveArtifacts artifacts: 'api_git_url.txt'  // Archive the Git URL details
                    }
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
