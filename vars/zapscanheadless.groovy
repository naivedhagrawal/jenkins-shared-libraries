def call() {
    pipeline {
        agent {
            kubernetes {
                yaml pod('zap', 'badouralix/curl-jq')
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
            stage('Run API Scan via Postman') {
                when {
                    expression { params.SCAN_TYPE == 'API' }
                }
                steps {
                    container ('zap') {
                        script {
                            if (!params.POSTMAN_COLLECTION_URL?.trim()) {
                                error "Postman Collection URL is required for API scan!"
                            }
                            echo "Downloading Postman Collection..."
                            sh "curl -L -o postman_collection.json ${params.POSTMAN_COLLECTION_URL}"
                            echo "Running Postman Collection with Newman..."
                            sh "newman run postman_collection.json --reporters cli,json --reporter-json-export postman_results.json"
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
                        }
                    }
                }
            }
            stage('ZAP Active Scan') {
                steps {
                    container ('zap') {
                        script {
                            def target = params.SCAN_TYPE == 'URL' ? params.TARGET_URL : "postman_results.json"
                            echo "Starting ZAP Active Scan on ${target}..."
                            def activeScan = sh(script: "curl -s \"${ZAP_URL}/JSON/ascan/action/scan/?url=${target}&recurse=true\" | jq -r '.scan'", returnStdout: true).trim()
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
                        }
                    }
                }
            }
            stage('Generate & Archive ZAP Report') {
                steps {
                    container ('zap') {
                        script {
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
                        }
                    }
                }
            }
        }
    }
}
