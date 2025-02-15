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
            choice(name: 'AUTH_REQUIRED', choices: ['yes', 'no'], description: 'Is authentication required?')
            string(name: 'LOGIN_URL', description: 'Login page URL')
            string(name: 'USERNAME', description: 'Username for authentication')
            password(name: 'PASSWORD', description: 'Password for authentication')
        }
        environment {
            ZAP_REPORT = 'zap-out.json'
            ZAP_REPORT_HTML = 'zap-out.html'
            ZAP_MD = 'zap-report.md'
            ZAP_CMD_REPORT = 'zap_cmd_report.html'
        }

        stages {
            stage('Validate Parameters') {
                steps {
                    script {
                        def TARGET_URL = params.target_URL?.trim()
                        if ((params.scanType == 'full-scan' || params.scanType == 'baseline' || params.scanType == 'zap_cmd') && (!TARGET_URL || TARGET_URL == '')) {
                            error('ERROR: TARGET_URL is required for the selected scan type.')
                        }

                        if (!TARGET_URL.startsWith('http')) {
                            error('ERROR: Target URL must start with http or https.')
                        }
                        
                        if (params.AUTH_REQUIRED == 'yes') {
                            if (!params.LOGIN_URL || !params.USERNAME || !params.PASSWORD) {
                                error('ERROR: Authentication is required but LOGIN_URL, USERNAME, or PASSWORD is missing.')
                            }
                            if (!params.LOGIN_URL.startsWith('http')) {
                                error('ERROR: Login URL must start with http or https.')
                            }
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
                            // Set system limits before running ZAP
                            sh 'ulimit -a' // Display current limits
                            sh 'ulimit -u 100000 || true' // Set process limit
                            sh 'ulimit -n 1048576 || true' // Set open file limit
                            sh 'echo $ZAP_URL'
                            sh 'sleep 60'
                            sh 'zap-cli status'

                            // Save the TARGET_URL and authentication details to a file
                            writeFile file: 'target_url.txt', text: "Target URL: ${TARGET_URL}\nLogin URL: ${params.LOGIN_URL}"

                            // Configure authentication in ZAP if required
                            if (params.AUTH_REQUIRED == 'yes') {
                                sh """
                                    zap-cli open-url '${params.LOGIN_URL}'
                                    zap-cli session set-context-user 'Default Context' 'auth_user'
                                    zap-cli session set-authentication-credentials 'auth_user' username='${params.USERNAME}' password='${params.PASSWORD}'
                                """
                            }

                            switch (params.scanType) {
                                case 'full-scan':
                                    sh "zap-full-scan.py -t '$TARGET_URL'" + (params.AUTH_REQUIRED == 'yes' ? " --auth-login-url '${params.LOGIN_URL}' --auth-username '${params.USERNAME}' --auth-password '${params.PASSWORD}'" : "") + " -J '$ZAP_REPORT' -r '$ZAP_REPORT_HTML' -w '$ZAP_MD' -I"
                                    sh 'mv /zap/wrk/${ZAP_REPORT} .'
                                    sh 'mv /zap/wrk/${ZAP_REPORT_HTML} .'
                                    sh 'mv /zap/wrk/${ZAP_MD} .'
                                    archiveArtifacts artifacts: "${ZAP_REPORT}"
                                    archiveArtifacts artifacts: "${ZAP_REPORT_HTML}"
                                    archiveArtifacts artifacts: "${ZAP_MD}"
                                    break
                                case 'baseline':
                                    sh "zap-baseline.py -t '$TARGET_URL'" + (params.AUTH_REQUIRED == 'yes' ? " --auth-login-url '${params.LOGIN_URL}' --auth-username '${params.USERNAME}' --auth-password '${params.PASSWORD}'" : "") + " -J '$ZAP_REPORT' -r '$ZAP_REPORT_HTML' -w '$ZAP_MD' -I"
                                    sh 'mv /zap/wrk/${ZAP_REPORT} .'
                                    sh 'mv /zap/wrk/${ZAP_REPORT_HTML} .'
                                    sh 'mv /zap/wrk/${ZAP_MD} .'
                                    archiveArtifacts artifacts: "${ZAP_REPORT}"
                                    archiveArtifacts artifacts: "${ZAP_REPORT_HTML}"
                                    archiveArtifacts artifacts: "${ZAP_MD}"
                                    break
                                case 'zap_cmd':
                                    sh "zap.sh -cmd -quickurl '${TARGET_URL}' -quickout /zap/wrk/${ZAP_CMD_REPORT} -quickprogress"
                                    sh 'mv /zap/wrk/${ZAP_CMD_REPORT} .'
                                    archiveArtifacts artifacts: "${ZAP_CMD_REPORT}"
                                    break
                            }
                        }
                        archiveArtifacts artifacts: 'target_url.txt'  // Archive the target URL details
                    }
                }
            }
        }
    }
}
