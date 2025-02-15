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
            ZAP_API_KEY_PATH = "/zap/wrk/api-key.txt"
        }
        stages {
            stage('DAST SCANNING USING OWASP-ZAP') {
                agent {
                    kubernetes {
                        yaml zap()
                    }
                }
                steps {
                    container('zap') {
                        script {
                            // Wait for ZAP to be ready
                            sh "for i in {1..30}; do curl -s http://localhost:8080 && break || sleep 5; done"

                            // Get API Key from the container
                            def zapApiKey = sh(script: "cat ${ZAP_API_KEY_PATH}", returnStdout: true).trim()
                            sh "echo 'Using API Key: ${zapApiKey}'"

                            // Check if ZAP is running
                            sh "zap-cli --zap-url http://localhost:8080 --api-key ${zapApiKey} status"

                            // Run Full Scan
                            sh "zap-full-scan.py -t '${params.target_URL}' --api-key ${zapApiKey} -J zap-out.json"
                        }
                    }
                }
            }
        }
    }
}
