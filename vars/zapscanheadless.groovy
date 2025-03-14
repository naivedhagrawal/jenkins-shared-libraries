def call() {
    pipeline {
        agent {
            kubernetes {
                yaml pod('zap', 'badouralix/curl-jq')
                showRawYaml false
            }
        }
        parameters {
            string(name: 'TARGET_URL', defaultValue: 'http://demo.testfire.net', description: 'Enter the target URL for scanning')
        }
        environment {
            ZAP_URL = "http://zap.devops-tools.svc.cluster.local:8090"
        }
        stages {
            stage('Check ZAP Availability') {
                steps {
                    container('zap') {
                        script {
                            echo "Checking if ZAP is responding..."
                            def zapStatus = sh(script: "curl -s --fail ${ZAP_URL}/JSON/core/view/version/ || echo 'DOWN'", returnStdout: true).trim()
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
                    container('zap') {
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
                            sh "curl -s \"${ZAP_URL}/OTHER/core/other/jsonreport/\" -o /home/zap/zap-report.json"
                            sh "curl -s \"${ZAP_URL}/OTHER/core/other/htmlreport/?title=Enhanced+ZAP+Report\" -o /home/zap/zap-enhanced-report.html"
                            sh "curl -s \"${ZAP_URL}/OTHER/core/other/htmlreport/?title=ZAP%20Security%20Report&template=traditional\" -o /home/zap/zap-traditional-report.html"
                            def generateModernReport = sh(script: "curl -s \"${ZAP_URL}/JSON/reports/action/generate/?title=ZAP%20Security%20Report&template=modern&reportDir=/home/zap/&reportFileName=modern-report.html\" | jq -r '.success'", returnStdout: true).trim()

                            if (generateModernReport != "true") {
                                error "Generation of modern report failed."
                            }

                            // Wait until file exists.
                            timeout(time: 3, unit: 'MINUTES') {
                                waitUntil {
                                    def fileExist = sh(script: 'test -f /home/zap/modern-report.html && echo "true" || echo "false"', returnStdout: true).trim()
                                    echo "Waiting for modern report to exist: ${fileExist}"
                                    sleep 5
                                    return fileExist == "true"
                                }
                            }

                            sh 'ls -l /home/zap/'
                            echo "Copying ZAP Reports to Jenkins workspace..."
                            sh "cp -r /home/zap/*.html /home/zap/*.json  \$(pwd)/"
                            sh 'ls -l'
                            echo "Archiving ZAP Reports..."
                            archiveArtifacts artifacts: 'zap-traditional-report.html, zap-enhanced-report.html, modern-report.html, zap-report.json', fingerprint: true
                        }
                    }
                }
            }
        }
    }
}
