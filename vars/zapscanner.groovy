def call() {
    pipeline {
        agent none
        parameters {
            string(name: 'target_URL', description: 'Target URL for DAST scan')
            choice(
                name: 'scanType',
                description: '''Full Scan - Full scan including active attacks
Baseline Scan - Passive scan without attacking the application''',
                choices: ['full-scan', 'baseline']
            )
        }
        environment {
            ZAP_REPORT = 'zap-out.json'
            ZAP_REPORT_HTML = 'zap-out.html'
            ZAP_SARIF = 'zap_report.sarif'
            ZAP_MD = 'zap-report.md'
            TARGET_URL = "${params.target_URL?.trim()}"
        }

        stages {
            stage('Validate Parameters') {
                steps {
                    script {
                        if ((params.scanType == 'full-scan' || params.scanType == 'baseline') && (!TARGET_URL || TARGET_URL == '')) {
                            error('ERROR: Target URL cannot be empty.')
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
                            // Save the TARGET_URL to a file
                            writeFile file: 'target_url.txt', text: "Target URL: ${TARGET_URL}"

                            switch (params.scanType) {
                                case 'full-scan':
                                    sh "zap-full-scan.py -t '$TARGET_URL' -J '$ZAP_REPORT' -r '$ZAP_REPORT_HTML' -w '$ZAP_MD' -I"
                                    break
                                case 'baseline':
                                    sh 'ls -lrt'
                                    sh "zap-baseline.py -t '$TARGET_URL' -J '$ZAP_REPORT' -r '$ZAP_REPORT_HTML' -w '$ZAP_MD' -I"
                                    break
                            }
                            sh 'mv /zap/wrk/${ZAP_REPORT} .' 
                            sh 'mv /zap/wrk/${ZAP_REPORT_HTML} .'
                            sh 'mv /zap/wrk/${ZAP_MD} .'
                        }
                        archiveArtifacts artifacts: "${ZAP_REPORT}"
                        archiveArtifacts artifacts: "${ZAP_REPORT_HTML}"
                        archiveArtifacts artifacts: "${ZAP_MD}"
                        archiveArtifacts artifacts: 'target_url.txt'  // Archive the target URL details
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
