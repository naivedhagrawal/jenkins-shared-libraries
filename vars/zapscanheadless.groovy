def call() {
    pipeline {
        agent {
            kubernetes {
                yaml zapheadlesspod()
                showRawYaml false
            }
        }
        parameters {
            string(name: 'TARGET_URL', defaultValue: 'http://demo.testfire.net', description: 'Enter the target URL for scanning')
        }
        environment {
            ZAP_URL = "http://0.0.0.0:8090"
        }
        stages {
            stage('Check ZAP Availability') {
                steps {
                    container ('curl-jq') {
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
                    container ('curl-jq') {
                        script {
                            echo "Starting ZAP Spider Scan on ${params.TARGET_URL}..."
                            def spiderScan = sh(script: "curl -s --fail \"${ZAP_URL}/JSON/spider/action/scan/?url=${params.TARGET_URL}\" | jq -r '.scan'", returnStdout: true).trim()
                            if (!(spiderScan ==~ /\d+/)) {
                                error "Spider scan failed!"
                            }
                            echo "Spider Scan ID: ${spiderScan}"
                            
                            echo "Waiting for Spider Scan to Complete..."
                            waitUntil {
                                def status = sh(script: "curl -s \"${ZAP_URL}/JSON/spider/view/status/?scanId=${spiderScan}\" | jq -r '.status'", returnStdout: true).trim()
                                echo "Spider Scan Progress: ${status}%"
                                sleep 5
                                return status == "100"
                            }
                            echo "Spider Scan Completed!"
/*
                            echo "Starting ZAP Active Scan on ${params.TARGET_URL}..."
                            def activeScan = sh(script: "curl -s \"${ZAP_URL}/JSON/ascan/action/scan/?url=${params.TARGET_URL}&recurse=true\" | jq -r '.scan'", returnStdout: true).trim()
                            if (!(activeScan ==~ /\d+/)) {
                                error "Active scan failed!"
                            }
                            echo "Active Scan ID: ${activeScan}"
                            
                            echo "Waiting for Active Scan to Complete..."
                            waitUntil {
                                def status = sh(script: "curl -s \"${ZAP_URL}/JSON/ascan/view/status/?scanId=${activeScan}\" | jq -r '.status'", returnStdout: true).trim()
                                echo "Active Scan Progress: ${status}%"
                                sleep 5
                                return status == "100"
                            }
                            echo "Active Scan Completed!"
*/
                            echo "Generating Enhanced ZAP Reports..."
                            sh "curl -s \"${ZAP_URL}/OTHER/core/other/jsonreport/\" -o zap-report.json"
                            sh "curl -s \"${ZAP_URL}/OTHER/core/other/htmlreport/?title=Enhanced+ZAP+Report\" -o zap-enhanced-report.html"
                            sh "curl -s \"${ZAP_URL}/OTHER/core/other/htmlreport/?title=ZAP%20Security%20Report&template=traditional\" -o zap-traditional-report.html"
                            sh "curl -s \"${ZAP_URL}/JSON/reports/action/generate/?title=ZAP%20Security%20Report&template=modern&reportDir=/home/zap/&reportFileName=modern-report.html\""                            
                            sh "curl -s -o modern-report.html \"${ZAP_URL}/OTHER/core/other/file/?name=modern-report.html\""
                            sh 'ls -l'
                            echo "Archiving Enhanced ZAP Reports..."
                            archiveArtifacts artifacts: 'zap-traditional-report.html, zap-enhanced-report.html, modern-report.html, zap-report.json', fingerprint: true
                        }
                        container ('zap') {
                            script {
                                sh "cp /home/zap/modern-report.html ."
                                archiveArtifacts artifacts: 'modern-report.html', fingerprint: true
                            }
                    }
                }
            }
        }
    }
}
}