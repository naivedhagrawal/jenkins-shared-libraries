def call() {
    pipeline {
    agent {
                    kubernetes {
                        yaml zap()
                        showRawYaml false
                    }
                }
    
    environment {
        ZAP_URL = 'http://zap.devops-tools.svc.cluster.local:8090'  // Your hosted ZAP URL
        TARGET_URL = ''  // This will be populated at runtime via parameters
    }
    
    parameters {
        string(name: 'TARGET_URL', defaultValue: 'https://google-gruyere.appspot.com/', description: 'Target URL for ZAP scan')
    }
    
    stages {
        stage('Start ZAP Spider') {
            steps {
                script {
                    if (!params.TARGET_URL) {
                        error("TARGET_URL is required.")
                    }
                    
                    echo "Starting ZAP spidering on target: ${params.TARGET_URL}"
                    
                    // Start ZAP Spider to crawl the target URL
                    def spiderUrl = "${ZAP_URL}/JSON/spider/action/scan"
                    def spiderResponse = sh(script: """
                        curl -s -X GET "${spiderUrl}?url=${params.TARGET_URL}"
                    """, returnStdout: true)

                    echo "Spider response: ${spiderResponse}"
                }
            }
        }

        stage('Start ZAP Scan') {
            steps {
                script {
                    echo "Starting ZAP scan on target: ${params.TARGET_URL}"
                    
                    // Triggering the ZAP scan after the spider
                    def scanUrl = "${ZAP_URL}/JSON/ascan/action/scan"
                    def scanResponse = sh(script: """
                        curl -s -X GET "${scanUrl}?url=${params.TARGET_URL}&maxDepth=5&maxChildren=10"
                    """, returnStdout: true)

                    echo "Scan initiated, response: ${scanResponse}"
                }
            }
        }

        stage('Check Scan Status') {
            steps {
                script {
                    // Checking scan status periodically
                    def statusUrl = "${ZAP_URL}/JSON/ascan/view/status"
                    def status = 0
                    while (status < 100) {
                        echo "Checking scan progress..."
                        def response = sh(script: """
                            curl -s -X GET "${statusUrl}?scanId=0"
                        """, returnStdout: true)
                        
                        status = readJSON(text: response).scanStatus.toInteger()
                        echo "Current scan status: ${status}%"
                        if (status < 100) {
                            sleep(time: 10, unit: 'SECONDS')
                        }
                    }
                    echo "Scan completed."
                }
            }
        }

        stage('Get Alerts') {
            steps {
                script {
                    // Fetching alerts from ZAP
                    def alertUrl = "${ZAP_URL}/JSON/core/view/alerts"
                    def response = sh(script: """
                        curl -s -X GET "${alertUrl}?baseurl=${params.TARGET_URL}"
                    """, returnStdout: true)

                    def alerts = readJSON(text: response)
                    echo "Found ${alerts.alerts.size()} alerts."
                    // You can further parse alerts here and take action like sending them to a report or email.
                }
            }
        }
    }

    post {
        always {
            echo "ZAP Scan Pipeline Finished"
        }
    }
}

}
