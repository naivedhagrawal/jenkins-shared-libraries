def call() {
pipeline {
    agent any
    parameters {
        string(name: 'TARGET_URL', defaultValue: 'https://google-gruyere.appspot.com/', description: 'Enter the target URL for scanning')
    }
    environment {
        ZAP_URL = "http://zap.devops-tools.svc.cluster.local:30090"
    }
    stages {
        stage('Start ZAP Scan') {
            steps {
                script {
                    echo "Starting ZAP Spider Scan on ${params.TARGET_URL}..."
                    def spiderScan = sh(script: "curl -s \"${ZAP_URL}/JSON/spider/action/scan/?url=${params.TARGET_URL}\"", returnStdout: true).trim()
                    echo "Spider Scan ID: ${spiderScan}"
                    
                    echo "Waiting for Spider Scan to Complete..."
                    waitUntil {
                        def status = sh(script: "curl -s \"${ZAP_URL}/JSON/spider/view/status/?scanId=${spiderScan}\" | jq -r '.status'", returnStdout: true).trim()
                        return status == "100"
                    }
                    echo "Spider Scan Completed!"
                }
            }
        }

        stage('Start Active Scan') {
            steps {
                script {
                    echo "Starting ZAP Active Scan on ${params.TARGET_URL}..."
                    def activeScan = sh(script: "curl -s \"${ZAP_URL}/JSON/ascan/action/scan/?url=${params.TARGET_URL}\"", returnStdout: true).trim()
                    echo "Active Scan ID: ${activeScan}"
                    
                    echo "Waiting for Active Scan to Complete..."
                    waitUntil {
                        def status = sh(script: "curl -s \"${ZAP_URL}/JSON/ascan/view/status/?scanId=${activeScan}\" | jq -r '.status'", returnStdout: true).trim()
                        return status == "100"
                    }
                    echo "Active Scan Completed!"
                }
            }
        }

        stage('Generate Advanced Report') {
            steps {
                script {
                    echo "Generating Advanced ZAP Report..."
                    sh "curl -s \"${ZAP_URL}/OTHER/core/other/report/?title=ZAP%20Security%20Report&template=traditional-html&reportFileName=advanced-zap-report.html&theme=corporate\" -o advanced-zap-report.html"
                }
            }
        }

        stage('Publish Report') {
            steps {
                publishHTML(target: [
                    reportDir: '.', 
                    reportFiles: 'advanced-zap-report.html', 
                    reportName: 'OWASP ZAP Security Report'
                ])
            }
        }
    }
}
}