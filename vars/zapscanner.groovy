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
            ZAP_REPORT_XML = 'zap-out.xml'
            ZAP_REPORT_PDF = 'zap-out.pdf'
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
                                    sh "zap-full-scan.py -t '$TARGET_URL' -I"
                                    break
                                case 'baseline':
                                    sh "zap-baseline.py -t '$TARGET_URL' -I"
                                    break
                            }

                            // Generate modern reports using report.py
                            sh "python3 /zap/scripts/report.py -o ${ZAP_REPORT_HTML} -f modern-html"
                            sh "python3 /zap/scripts/report.py -o ${ZAP_REPORT_XML} -f modern-xml"
                            sh "python3 /zap/scripts/report.py -o ${ZAP_REPORT_PDF} -f modern-pdf"
                        }
                        archiveArtifacts artifacts: "zap-out.*"
                        archiveArtifacts artifacts: 'target_url.txt'  // Archive the target URL details
                    }
                }
            }
        }
    }
}
