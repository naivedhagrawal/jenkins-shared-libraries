def call() {
    pipeline {
        agent {
            kubernetes {
                yaml pod('zap', 'naivedh/curl_jq_newman_pdf:latest')
                showRawYaml false
            }
        }
        parameters {
            choice(name: 'SCAN_TYPE', choices: ['URL', 'API'], description: 'Select scan type: URL or API')
            string(name: 'TARGET_URL', defaultValue: 'http://demo.testfire.net', description: 'Enter the target URL for scanning')
            string(name: 'POSTMAN_COLLECTION_URL', defaultValue: '', description: 'Enter the Git URL of the Postman collection (required for API scan)')
        }
        environment {
            ZAP_URL = "http://zap.devops-tools.svc.cluster.local:8090"
        }
        stages {
            stage('Check ZAP Availability') {
                steps {
                    container ('zap') {
                        script {
                            try {
                                echo "Checking if ZAP is responding..."
                                def zapStatus = sh(script: "curl -s --fail ${ZAP_URL} || echo 'DOWN'", returnStdout: true).trim()
                                if (zapStatus == 'DOWN') {
                                    error "ZAP is not responding! Error details: ${zapStatus}"
                                }
                                echo "ZAP is up and running."
                            } catch (Exception e) {
                                error "ZAP availability check failed: ${e.message}"
                            }
                        }
                    }
                }
            }
            stage('Run API Scan via Postman') {
                when {
                    expression { params.SCAN_TYPE == 'API' }
                }
                steps {
                    container ('zap') {
                        script {
                            try {
                                if (!params.POSTMAN_COLLECTION_URL?.trim()) {
                                    error "Postman Collection URL is required for API scan!"
                                }
                                if (!params.POSTMAN_COLLECTION_URL.startsWith('http')) {
                                    error "Invalid Postman Collection URL. Ensure it starts with 'http'."
                                }
                                echo "Downloading Postman Collection..."
                                sh "curl -L -o postman_collection.json ${params.POSTMAN_COLLECTION_URL}"
                                echo "Running Postman Collection with Newman..."
                                sh "newman run postman_collection.json --reporters cli,json --reporter-json-export postman_results.json"
                            } catch (Exception e) {
                                error "API scan failed: ${e.message}"
                            }
                        }
                    }
                }
            }
            stage('ZAP Spider Scan') {
                when {
                    expression { params.SCAN_TYPE == 'URL' }
                }
                steps {
                    container ('zap') {
                        script {
                            try {
                                echo "Starting ZAP Spider Scan on ${params.TARGET_URL}..."
                                def spiderScan = sh(script: "curl -s --fail \"${ZAP_URL}/JSON/spider/action/scan/?url=${params.TARGET_URL}\" | jq -r '.scan'", returnStdout: true).trim()
                                if (!(spiderScan ==~ /\d+/)) {
                                    error "Spider scan failed! Scan ID: ${spiderScan}"
                                }
                                echo "Spider Scan ID: ${spiderScan}"
                                
                                echo "Waiting for Spider Scan to Complete..."
                                def status = ''
                                while (status != "100") {
                                    status = sh(script: "curl -s \"${ZAP_URL}/JSON/spider/view/status/?scanId=${spiderScan}\" | jq -r '.status'", returnStdout: true).trim()
                                    echo "Spider Scan Progress: ${status}%"
                                    sleep 5
                                }
                                echo "Spider Scan Completed!"
                            } catch (Exception e) {
                                error "ZAP Spider Scan failed: ${e.message}"
                            }
                        }
                    }
                }
            }
            stage('ZAP Active Scan') {
                when {
                    expression { params.SCAN_TYPE == 'URL' }
                }
                steps {
                    container ('zap') {
                        script {
                            try {
                                def target = params.SCAN_TYPE == 'URL' ? params.TARGET_URL : "postman_results.json"
                                echo "Starting ZAP Active Scan on ${target}..."
                                def activeScan = sh(script: "curl -s \"${ZAP_URL}/JSON/ascan/action/scan/?url=${target}&recurse=true\" | jq -r '.scan'", returnStdout: true).trim()
                                if (!(activeScan ==~ /\d+/)) {
                                    error "Active scan failed! Scan ID: ${activeScan}"
                                }
                                echo "Active Scan ID: ${activeScan}"
                                
                                echo "Waiting for Active Scan to Complete..."
                                def status = ''
                                while (status != "100") {
                                    status = sh(script: "curl -s \"${ZAP_URL}/JSON/ascan/view/status/?scanId=${activeScan}\" | jq -r '.status'", returnStdout: true).trim()
                                    echo "Active Scan Progress: ${status}%"
                                    sleep 5
                                }
                                echo "Active Scan Completed!"
                            } catch (Exception e) {
                                error "ZAP Active Scan failed: ${e.message}"
                            }
                        }
                    }
                }
            }
            stage('Generate & Archive ZAP Report') {
                steps {
                    container ('zap') {
                        script {
                            try {
                                echo "Generating Modern ZAP Report..."
                                def buildName = "zap-report-${env.BUILD_NUMBER}.html"
                                def reportFolder = "zap-report-${env.BUILD_NUMBER}"
                                sh "curl -s \"${ZAP_URL}/JSON/reports/action/generate/?title=ZAP%20Security%20Report&template=modern&reportDir=/zap/reports/&reportFileName=${buildName}\""
                                sh "cp -r /zap/reports/${buildName} ."
                                sh "cp -r /zap/reports/${reportFolder} ."
                                echo "Setting Build Name: ${buildName}"
                                currentBuild.displayName = buildName
                                echo "Archiving Modern ZAP Report..."
                                archiveArtifacts artifacts: "${buildName}, ${reportFolder}/**", fingerprint: true
                                
                                // Post-scan action: Check for high-severity vulnerabilities
                                def zapResults = sh(script: "curl -s \"${ZAP_URL}/JSON/core/view/alerts\" | jq '.alerts[] | select(.risk == \"High\")'", returnStdout: true).trim()
                                if (zapResults) {
                                    error "High severity vulnerabilities detected. Build failed!"
                                }
                            } catch (Exception e) {
                                error "Failed to generate or archive ZAP report: ${e.message}"
                            }
                        }
                    }
                }
            }
        }
    }
}
