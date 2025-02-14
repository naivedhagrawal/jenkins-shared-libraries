def call() {
    pipeline {
        agent none
        parameters {
            string(name: 'target_URL', description: 'Target URL for DAST scan')
            choice(
                name: 'scanType',
                description: '''Full Scan - Full scan including active attacks
Baseline Scan - Passive scan without attacking the application
ZAP Command - Custom ZAP command execution''',
                choices: ['full-scan', 'baseline', 'zap_cmd']
            )
        }
        environment {
            ZAP_REPORT = 'zap-out.json'
            ZAP_REPORT_HTML = 'zap-out.html'
            ZAP_SARIF = 'zap_report.sarif'
            ZAP_MD = 'zap-report.md'
            ZAP_CMD_REPORT = 'zap_cmd_report.html'
            TARGET_URL = "${params.target_URL?.trim()}"
        }

        stages {
            stage('Validate Parameters') {
                steps {
                    script {
                        if ((params.scanType == 'full-scan' || params.scanType == 'baseline' || params.scanType == 'zap_cmd') && (!TARGET_URL || TARGET_URL == '')) {
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
                                    sh "zap-baseline.py -t '$TARGET_URL' -J '$ZAP_REPORT' -r '$ZAP_REPORT_HTML' -w '$ZAP_MD' -I -T modern"
                                    break
                                case 'zap_cmd':
                                    sh "zap.sh -cmd -quickurl '$TARGET_URL' -quickout '$ZAP_CMD_REPORT' -quickprogress"
                                    break
                            }
                            sh 'mv /zap/wrk/${ZAP_REPORT} .'
                            sh 'mv /zap/wrk/${ZAP_REPORT_HTML} .'
                            sh 'mv /zap/wrk/${ZAP_MD} .'
                            sh 'mv /zap/${ZAP_CMD_REPORT} .'
                        }
                        archiveArtifacts artifacts: "${ZAP_REPORT}"
                        archiveArtifacts artifacts: "${ZAP_REPORT_HTML}"
                        archiveArtifacts artifacts: "${ZAP_MD}"
                        archiveArtifacts artifacts: "${ZAP_CMD_REPORT}"
                        archiveArtifacts artifacts: 'target_url.txt'  // Archive the target URL details
                    }
                }
            }
        }
    }
}
