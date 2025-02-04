def call() {    
    pipeline {
        agent none
        environment {
            ZAP_REPORT = 'zap-out.json'
            ZAP_SARIF = 'zap_report.sarif'
            TARGET_URL = 'https://juice-shop.herokuapp.com/'
        }

        stages {
            stage('Owasp zap') {
                agent {
                    kubernetes {
                        yaml zap()
                        showRawYaml false
                    }
                }
                steps {
                    container('zap') {
                        sh """
                            zap-full-scan.py -t $TARGET_URL -J $ZAP_REPORT -l WARN -I
                            mv /zap/wrk/${ZAP_REPORT} .
                        """
                        archiveArtifacts artifacts: "${env.ZAP_REPORT}"
                    }

                    container('python') {
                        script {
                            def jsonToSarif = libraryResource('zap_json_to_sarif.py')
                            writeFile file: 'zap_json_to_sarif.py', text: jsonToSarif
                            sh 'python3 zap_json_to_sarif.py ${inputJson} ${outputSarif}'
                        }
                        archiveArtifacts artifacts: "${env.ZAP_SARIF}"
                        recordIssues(
                            enabledForFailure: true,
                            tool: sarif(pattern: "${env.ZAP_SARIF}", id: "zap-SARIF", name: "DAST Report")
                        )
                    }
                }
            }
        }
    }
}
