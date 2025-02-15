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
                            // Retrieve API Key from file inside the container
                            def zapApiKey = sh(script: "cat /zap-api-key.txt", returnStdout: true).trim()
                            sh "echo 'Using API Key: ${zapApiKey}'"

                            // Verify ZAP is running
                            sh "curl http://localhost:8080"
                            sh "zap-cli --zap-url http://localhost:8080 --api-key ${zapApiKey} status"

                            // Save the TARGET_URL and authentication details to a file
                            writeFile file: 'target_url.txt', text: "Target URL: ${params.target_URL}\nLogin URL: ${params.LOGIN_URL}"

                            // Configure authentication in ZAP if required
                            if (params.AUTH_REQUIRED == 'yes') {
                                sh """
                                    zap-cli --zap-url http://localhost:8080 --api-key ${zapApiKey} open-url '${params.LOGIN_URL}'
                                    zap-cli --zap-url http://localhost:8080 --api-key ${zapApiKey} session set-context-user 'Default Context' 'auth_user'
                                    zap-cli --zap-url http://localhost:8080 --api-key ${zapApiKey} session set-authentication-credentials 'auth_user' username='${params.USERNAME}' password='${params.PASSWORD}'
                                """
                            }

                            switch (params.scanType) {
                                case 'full-scan':
                                    sh "zap-full-scan.py -t '${params.target_URL}' --api-key ${zapApiKey} -J '${ZAP_REPORT}' -r '${ZAP_REPORT_HTML}' -w '${ZAP_MD}' -I"
                                    break
                                case 'baseline':
                                    sh "zap-baseline.py -t '${params.target_URL}' --api-key ${zapApiKey} -J '${ZAP_REPORT}' -r '${ZAP_REPORT_HTML}' -w '${ZAP_MD}' -I"
                                    break
                                case 'zap_cmd':
                                    sh "zap.sh -cmd -quickurl '${params.target_URL}' --api-key ${zapApiKey} -quickout /zap/wrk/${ZAP_CMD_REPORT} -quickprogress"
                                    break
                            }

                            // Move reports to workspace
                            sh 'mv /zap/wrk/${ZAP_REPORT} .'
                            sh 'mv /zap/wrk/${ZAP_REPORT_HTML} .'
                            sh 'mv /zap/wrk/${ZAP_MD} .'
                            sh 'mv /zap/wrk/${ZAP_CMD_REPORT} .'

                            // Archive reports
                            archiveArtifacts artifacts: "${ZAP_REPORT}"
                            archiveArtifacts artifacts: "${ZAP_REPORT_HTML}"
                            archiveArtifacts artifacts: "${ZAP_MD}"
                            archiveArtifacts artifacts: "${ZAP_CMD_REPORT}"
                        }
                        archiveArtifacts artifacts: 'target_url.txt'  // Archive the target URL details
                    }
                }
            }
        }
    }
}
