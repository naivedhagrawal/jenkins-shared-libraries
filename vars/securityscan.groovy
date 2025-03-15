/* @Library('k8s-shared-lib') _
securityscan(
    gitleak: true,
    owaspdependency: true,
    semgrep: true,
    checkov: true,
)*/

def call(Map params = [gitleak: true, owaspdependency: true, semgrep: true, checkov: true, gitguardian: true]) {
    def GITLEAKS_REPORT = 'gitleaks-report'
    def OWASP_DEP_REPORT = 'owasp-dep-report'
    def SEMGREP_REPORT = 'semgrep-report'
    def CHECKOV_REPORT = 'results.sarif'

    pipeline {
        agent none

        stages {
            stage('Gitleak Check') {
                when { expression { params.gitleak } }
                agent {
                    kubernetes {
                        yaml pod('gitleak', 'zricethezav/gitleaks')
                        showRawYaml false
                    }
                }
                steps {
                    script {
                        container('gitleak') {
                            checkout scm
                            sh "gitleaks detect --source=. --report-path=${GITLEAKS_REPORT}.sarif --report-format sarif --exit-code=0"
                            /*sh "gitleaks detect --source=. --report-path=${GITLEAKS_REPORT}.json --report-format json --exit-code=0"
                            sh "gitleaks detect --source=. --report-path=${GITLEAKS_REPORT}.csv --report-format csv --exit-code=0"*/
                            recordIssues(
                                enabledForFailure: true,
                                tool: sarif(
                                    pattern: "${GITLEAKS_REPORT}.sarif",
                                    id: "Git-Leaks",
                                    name: "Secret Scanning Report"
                                )
                            )
                            archiveArtifacts artifacts: "${GITLEAKS_REPORT}.*"
                        }
                    }
                }
            }

            stage('OWASP Dependency Check') {
                when { expression { params.owaspdependency } }
                agent {
                    kubernetes {
                        yaml pod('owasp', 'owasp/dependency-check:latest')
                        showRawYaml false
                    }
                }
                steps {
                    script {
                        container('owasp') {
                            withCredentials([string(credentialsId: 'NVD_API_KEY', variable: 'NVD_API_KEY')]) {
                                checkout scm
                                sh """
                                    mkdir -p reports
                                    /usr/share/dependency-check/bin/dependency-check.sh --scan . \
                                        --format "SARIF" \
                                        --format "JSON" \
                                        --format "CSV" \
                                        --format "XML" \
                                        --exclude "**/*.zip" \
                                        --out "reports/" \
                                        --nvdApiKey "\${NVD_API_KEY}"
                                    
                                    mv reports/dependency-check-report.sarif ${OWASP_DEP_REPORT}.sarif
                                    mv reports/dependency-check-report.json ${OWASP_DEP_REPORT}.json
                                    mv reports/dependency-check-report.csv ${OWASP_DEP_REPORT}.csv
                                    mv reports/dependency-check-report.xml ${OWASP_DEP_REPORT}.xml
                                """
                                recordIssues(
                                    enabledForFailure: true,
                                    tool: sarif(
                                        pattern: "${OWASP_DEP_REPORT}.sarif",
                                        id: "Owasp-Dependency-Check",
                                        name: "Dependency Check Report"
                                    )
                                )
                                archiveArtifacts artifacts: "${OWASP_DEP_REPORT}.*"
                            }
                        }
                    }
                }
            }


            stage('Semgrep Scan') {
                when { expression { params.semgrep } }
                agent {
                    kubernetes {
                        yaml pod('semgrep', 'returntocorp/semgrep:latest')
                        showRawYaml false
                    }
                }
                steps {
                    script {
                        container('semgrep') {
                            checkout scm
                            withCredentials([string(credentialsId: 'SEMGREP_KEY', variable: 'SEMGREP_KEY')]) {
                                sh "mkdir -p reports"
                                sh "semgrep --config=auto --sarif --output reports/semgrep.sarif ."
                                /*sh "semgrep --config=auto --json --output reports/semgrep.json ."
                                sh "semgrep --config=auto --verbose --output reports/semgrep.txt ."*/
                                archiveArtifacts artifacts: "reports/semgrep.*"
                                recordIssues(
                                    enabledForFailure: true,
                                    tool: sarif(
                                        pattern: "reports/semgrep.sarif",
                                        id: "SEMGREP-SAST",
                                        name: "SAST Report"
                                    )
                                )
                            }
                        }
                    }
                }
            }
            stage('Trivy Scan') {
                when { expression { params.trivy } }
                agent {
                    kubernetes {
                        yaml pod('trivy', 'aquasec/trivy:latest')
                        showRawYaml false
                    }
                }
                steps {
                    script {
                        container('trivy') {
                            checkout scm
                            sh "mkdir -p reports"

                            def scanTypes = []
                            def target = "."

                            // Detect applicable scan types
                            if (fileExists('Dockerfile')) {
                                scanTypes.add("image")
                            }
                            if (fileExists('.git')) {
                                scanTypes.add("repo")
                            }
                            if (sh(script: "find . -name '*.yaml' | grep -q .", returnStatus: true) == 0) {
                                scanTypes.add("k8s")
                            }
                            if (sh(script: "find . -name '*.tf' | grep -q .", returnStatus: true) == 0) {
                                scanTypes.add("config")
                            }
                            if (sh(script: "find . -name '*.aws' | grep -q .", returnStatus: true) == 0) {
                                scanTypes.add("aws")
                            }
                            if (scanTypes.isEmpty()) {
                                scanTypes.add("fs")  // Default to filesystem scan
                            }

                            // Execute each scan
                            scanTypes.each { scanType ->
                                sh "echo 'Running Trivy ${scanType} scan...'"

                                def reportName = "trivy-${scanType}.sarif"

                                if (scanType == 'fs') {
                                    sh "trivy fs ${target} --format sarif --output reports/${reportName} || true"
                                } else if (scanType == 'image') {
                                    sh "trivy image mydockerimage:latest --format sarif --output reports/${reportName} || true"
                                } else if (scanType == 'repo') {
                                    sh "trivy repo ${target} --format sarif --output reports/${reportName} || true"
                                } else if (scanType == 'k8s') {
                                    sh "trivy k8s cluster --format sarif --output reports/${reportName} || true"
                                } else if (scanType == 'config') {
                                    sh "trivy config ${target} --format sarif --output reports/${reportName} || true"
                                } else if (scanType == 'aws') {
                                    sh "trivy aws --format sarif --output reports/${reportName} || true"
                                }
                            }

                            // Always run secret scan
                            sh "trivy secret ${target} --format sarif --output reports/trivy-secret.sarif || true"

                            // Record all reports
                            recordIssues(
                                enabledForFailure: true,
                                tool: sarif(
                                    pattern: "reports/trivy-*.sarif",
                                    id: "Trivy-Vulnerability-Scan",
                                    name: "Trivy Report"
                                )
                            )
                            archiveArtifacts artifacts: "reports/trivy-*.sarif"
                        }
                    }
                }
            }


            stage('Checkov Scan') {
                when { expression { params.checkov } }
                agent {
                    kubernetes {
                        yaml pod('checkov', 'bridgecrew/checkov:latest')
                        showRawYaml false
                    }
                }
                steps {
                    script {
                        container('checkov') {
                            checkout scm
                            sh "checkov --directory . --output sarif || true"
                            recordIssues(
                                enabledForFailure: true,
                                tool: sarif(
                                    pattern: "${CHECKOV_REPORT}",
                                    id: "Checkov-IaC",
                                    name: "Checkov Report"
                                )
                            )
                            archiveArtifacts artifacts: "${CHECKOV_REPORT}"
                        }
                    }
                }
            }
        }
    }
}
