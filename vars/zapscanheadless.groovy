def call() {
    pipeline {
        agent {
            kubernetes {
                yaml pod('zap', 'badouralix/curl-jq')
                showRawYaml false
            }
        }
        parameters {
            string(name: 'TARGET_URL', defaultValue: 'https://google-gruyere.appspot.com', description: 'Enter the target URL for scanning')
        }
        environment {
            ZAP_URL = "http://zap.devops-tools.svc.cluster.local:8090"
        }
        stages {
            stage('Check ZAP Availability') {
                steps {
                    container ('zap') {
                        script {
                            echo "Checking if ZAP is responding..."
                            def zapStatus = sh(script: "curl -s --fail ${ZAP_URL} || echo 'DOWN'", returnStdout: true).trim()
                            if (zapStatus == 'DOWN') {
                                error "ZAP is not responding!"
                            }
                            echo "ZAP is up and running."
                        }
                    }
                }
            }
            stage('ZAP Security Scan, Report Generation & Archival') {
                steps {
                    container ('zap') {
                        script {
                            echo "Starting ZAP Spider Scan on ${params.TARGET_URL}..."
                            def spiderScan = sh(script: "curl -s --fail \"${ZAP_URL}/JSON/spider/action/scan/?url=${params.TARGET_URL}\" | jq -r '.scan'", returnStdout: true).trim()
                            if (!spiderScan.isNumber()) {
                                error "Spider scan failed!"
                            }
                            echo "Spider Scan ID: ${spiderScan}"
                            
                            echo "Waiting for Spider Scan to Complete..."
                            waitUntil {
                                def status = sh(script: "curl -s \"${ZAP_URL}/JSON/spider/view/status/?scanId=${spiderScan}\" | jq -r '.status'", returnStdout: true).trim()
                                sleep 5
                                return status == "100"
                            }
                            echo "Spider Scan Completed!"

                            echo "Starting ZAP Active Scan on ${params.TARGET_URL}..."
                            def activeScan = sh(script: "curl -s \"${ZAP_URL}/JSON/ascan/action/scan/?url=${params.TARGET_URL}&recurse=true\" | jq -r '.scan'", returnStdout: true).trim()
                            if (!activeScan.isNumber()) {
                                error "Active scan failed!"
                            }
                            echo "Active Scan ID: ${activeScan}"
                            
                            echo "Waiting for Active Scan to Complete..."
                            waitUntil {
                                def status = sh(script: "curl -s \"${ZAP_URL}/JSON/ascan/view/status/?scanId=${activeScan}\" | jq -r '.status'", returnStdout: true).trim()
                                sleep 5
                                return status == "100"
                            }
                            echo "Active Scan Completed!"

                            echo "Generating Advanced ZAP Report..."
                            sh "curl -s \"${ZAP_URL}/OTHER/core/other/htmlreport/?title=ZAP%20Security%20Report\" -o advanced-zap-report.html"

                            echo "Archiving ZAP Report..."
                            archiveArtifacts artifacts: 'advanced-zap-report.html', fingerprint: true
                        }
                    }
                }
            }
        }
    }
}