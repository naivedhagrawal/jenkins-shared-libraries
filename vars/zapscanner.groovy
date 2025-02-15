def call() {
    pipeline {
    agent {
        kubernetes {
            yaml zap()
            showRawYaml false
        }
    }
    environment {
        ZAP_URL = 'http://zap.devops-tools.svc.cluster.local:8090'
        TARGET_URL = ''  // This will be populated at runtime
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
                    
                    // Extract the scan ID from the response
                    def scanId = readJSON(text: spiderResponse).scan.toInteger()
                    echo "Spider scan ID: ${scanId}"
                    
                    // Save the scan ID for later use
                    currentBuild.description = "Scan ID: ${scanId}"
                }
            }
        }
        
        stage('Start ZAP Scan') {
            steps {
                script {
                    // Get the scan ID from the previous stage (assuming it's stored in currentBuild.description)
                    def scanId = currentBuild.description.split(":")[1]?.trim()
                    if (!scanId) {
                        error("Scan ID is missing. Ensure that the spider completed successfully.")
                    }
                    
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
                    // Get the scan ID from the previous stage (assuming it's stored in currentBuild.description)
                    def scanId = currentBuild.description.split(":")[1]?.trim()
                    if (!scanId) {
                        error("Scan ID is missing. Ensure that the spider completed successfully.")
                    }

                    echo "Checking scan progress for scan ID: ${scanId}"
                    
                    def statusUrl = "${ZAP_URL}/JSON/ascan/view/status"
                    def status = 0
                    
                    // Polling the scan status
                    while (status < 100) {
                        echo "Checking scan progress..."
                        def response = sh(script: """
                            curl -s -X GET "${statusUrl}?scanId=${scanId}"
                        """, returnStdout: true)
                        
                        def jsonResponse = readJSON(text: response)
                        status = jsonResponse?.scanStatus?.toInteger() ?: 0  // Default to 0 if null
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
