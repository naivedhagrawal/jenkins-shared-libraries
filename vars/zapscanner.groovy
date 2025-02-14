stage('DAST SCANNING USING OWASP-ZAP') {
    agent {
        kubernetes {
            yaml zap()
            showRawYaml false
        }
    }
    parameters {
        choice(name: 'AUTH_REQUIRED', choices: ['yes', 'no'], description: 'Is authentication required?')
        string(name: 'LOGIN_URL', description: 'Login page URL')
        string(name: 'USERNAME', description: 'Username for authentication')
        password(name: 'PASSWORD', description: 'Password for authentication')
    }
    steps {
        container('zap') {
            script {
                // Set system limits before running ZAP
                sh 'ulimit -a' // Display current limits
                sh 'ulimit -u 100000 || true' // Set process limit
                sh 'ulimit -n 1048576 || true' // Set open file limit

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

                // Perform the scan based on selected scan type
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
                        sh "zap-baseline.py -t '$TARGET_URL'" + (params.AUTH_REQUIRED == 'yes' ? " --auth-login-url '${params.LOGIN_URL}' --auth-username '${params.USERNAME}' --auth-password '${params.PASSWORD}'" : "") + " -J '$ZAP_REPORT' -r '$ZAP_REPORT_HTML' -w '$ZAP_MD' -I -T modern"
                        sh 'mv /zap/wrk/${ZAP_REPORT} .'
                        sh 'mv /zap/wrk/${ZAP_REPORT_HTML} .'
                        sh 'mv /zap/wrk/${ZAP_MD} .'
                        archiveArtifacts artifacts: "${ZAP_REPORT}"
                        archiveArtifacts artifacts: "${ZAP_REPORT_HTML}"
                        archiveArtifacts artifacts: "${ZAP_MD}"
                        break
                    case 'zap_cmd':
                        sh "zap.sh -cmd -quickurl '${TARGET_URL}' -quickout '${ZAP_CMD_REPORT}' -quickprogress"
                        sh 'mv /zap/wrk/${ZAP_CMD_REPORT} .'
                        archiveArtifacts artifacts: "${ZAP_CMD_REPORT}"
                        break
                }
            }
            archiveArtifacts artifacts: 'target_url.txt'  // Archive target and login URL details
        }
    }
}
