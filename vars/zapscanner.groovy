pipeline {
    agent none

    parameters {
        string(name: 'TARGET_URL', description: 'Target URL for DAST scan')
        choice(
            name: 'SCAN_TYPE',
            description: '''full-scan - Full scan including active attacks
baseline - Passive scan without attacking the application
zap_cmd - Custom ZAP command execution''',
            choices: ['full-scan', 'baseline', 'zap_cmd']
        )
        choice(name: 'AUTH_REQUIRED', choices: ['yes', 'no'], description: 'Is authentication required?')
        string(name: 'LOGIN_URL', description: 'Login page URL (if required)')
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
                    def target = params.TARGET_URL?.trim()
                    if (!target || !target.startsWith('http')) {
                        error('ERROR: TARGET_URL must be a valid URL starting with http or https.')
                    }

                    if (params.AUTH_REQUIRED == 'yes') {
                        if (!params.LOGIN_URL || !params.USERNAME || !params.PASSWORD) {
                            error('ERROR: Authentication is required but LOGIN_URL, USERNAME, or PASSWORD is missing.')
                        }
                        if (!params.LOGIN_URL.startsWith('http')) {
                            error('ERROR: LOGIN_URL must be a valid URL.')
                        }
                    }
                }
            }
        }

        stage('DAST Scanning Using OWASP-ZAP') {
            agent {
                kubernetes {
                    yaml zap()
                    showRawYaml false
                }
            }
            steps {
                container('zap') {
                    script {
                        sh 'curl -s http://localhost:8080'

                        // Ensure ZAP is running
                        sh 'zap-cli --zap-url http://localhost:8080 status || echo "ZAP is not running!"'

                        // Save the target URL and authentication details
                        writeFile file: 'target_url.txt', text: "Target URL: ${params.TARGET_URL}\nLogin URL: ${params.LOGIN_URL}"

                        if (params.AUTH_REQUIRED == 'yes') {
                            sh """
                                zap-cli open-url '${params.LOGIN_URL}'
                                zap-cli session set-context-user 'Default Context' 'auth_user'
                                zap-cli session set-authentication-credentials 'auth_user' username='${params.USERNAME}' password='${params.PASSWORD}'
                            """
                        }

                        switch (params.SCAN_TYPE) {
                            case 'full-scan':
                                sh "zap-full-scan.py -t '${params.TARGET_URL}'" +
                                   (params.AUTH_REQUIRED == 'yes' ? " --auth-login-url '${params.LOGIN_URL}' --auth-username '${params.USERNAME}' --auth-password '${params.PASSWORD}'" : "") +
                                   " -J '${ZAP_REPORT}' -r '${ZAP_REPORT_HTML}' -w '${ZAP_MD}' -I"
                                sh 'mv /zap/wrk/${ZAP_REPORT} .'
                                sh 'mv /zap/wrk/${ZAP_REPORT_HTML} .'
                                sh 'mv /zap/wrk/${ZAP_MD} .'
                                archiveArtifacts artifacts: "${ZAP_REPORT}, ${ZAP_REPORT_HTML}, ${ZAP_MD}"
                                break
                            
                            case 'baseline':
                                sh "zap-baseline.py -t '${params.TARGET_URL}'" +
                                   (params.AUTH_REQUIRED == 'yes' ? " --auth-login-url '${params.LOGIN_URL}' --auth-username '${params.USERNAME}' --auth-password '${params.PASSWORD}'" : "") +
                                   " -J '${ZAP_REPORT}' -r '${ZAP_REPORT_HTML}' -w '${ZAP_MD}' -I"
                                sh 'mv /zap/wrk/${ZAP_REPORT} .'
                                sh 'mv /zap/wrk/${ZAP_REPORT_HTML} .'
                                sh 'mv /zap/wrk/${ZAP_MD} .'
                                archiveArtifacts artifacts: "${ZAP_REPORT}, ${ZAP_REPORT_HTML}, ${ZAP_MD}"
                                break

                            case 'zap_cmd':
                                sh "zap.sh -cmd -quickurl '${params.TARGET_URL}' -quickout /zap/wrk/${ZAP_CMD_REPORT} -quickprogress"
                                sh 'mv /zap/wrk/${ZAP_CMD_REPORT} .'
                                archiveArtifacts artifacts: "${ZAP_CMD_REPORT}"
                                break
                        }
                    }
                }
            }
        }
    }
}
