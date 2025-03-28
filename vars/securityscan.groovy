def call() {
    def GITLEAKS_REPORT = 'gitleaks-report'
    def OWASP_DEP_REPORT = 'owasp-dep-report'
    def SEMGREP_REPORT = 'semgrep-report'
    def CHECKOV_REPORT = 'results.sarif'
    def WORKSPACE_DIR = '/workspace'

    pipeline {
        agent none

        stages {
            stage('Git Checkout') {
                agent {
                    kubernetes {
                        yaml generatePodYaml([
                            [name: 'git', image: 'alpine/git']
                        ])
                        defaultContainer 'git'
                    }
                }
                steps {
                    dir(WORKSPACE_DIR) {
                        sh "git --version"
                        checkout scm
                    }
                }
            }

            stage('Security Scans') {
                matrix {
                    axes {
                        axis {
                            name 'TOOL'
                            values 'gitleak', 'owasp', 'semgrep', 'checkov'
                        }
                    }
                    agent {
                        kubernetes {
                            yaml generatePodYaml([
                                [name: TOOL, image: "${TOOL}-image"]
                            ])
                            defaultContainer TOOL
                        }
                    }
                    stages {
                        script {
                            def toolName = TOOL.capitalize()
                            stage("${toolName} Scan") {
                                steps {
                                    dir(WORKSPACE_DIR) {
                                        script {
                                            switch (TOOL) {
                                                case 'gitleak':
                                                    sh "gitleaks detect --source=. --report-path=${GITLEAKS_REPORT}.sarif --report-format sarif"
                                                    recordIssues(
                                                        tool: sarif(
                                                            pattern: "${GITLEAKS_REPORT}.sarif",
                                                            id: "Git-Leaks",
                                                            name: "Secret Scanning Report"
                                                        )
                                                    )
                                                    archiveArtifacts artifacts: "${GITLEAKS_REPORT}.*"
                                                    break

                                                case 'owasp':
                                                    sh """
                                                        mkdir -p reports
                                                        /usr/share/dependency-check/bin/dependency-check.sh --scan . \
                                                            --format "SARIF" \
                                                            --format "JSON" \
                                                            --format "CSV" \
                                                            --format "XML" \
                                                            --exclude "**/*.zip" \
                                                            --out "reports/"
                                                        
                                                        mv reports/dependency-check-report.sarif ${OWASP_DEP_REPORT}.sarif
                                                        mv reports/dependency-check-report.json ${OWASP_DEP_REPORT}.json
                                                        mv reports/dependency-check-report.csv ${OWASP_DEP_REPORT}.csv
                                                        mv reports/dependency-check-report.xml ${OWASP_DEP_REPORT}.xml
                                                    """
                                                    recordIssues(
                                                        tool: owaspDependencyCheck(
                                                            pattern: "${OWASP_DEP_REPORT}.json",
                                                            id: "Owasp-Dependency-Check",
                                                            name: "Dependency Check Report"
                                                        )
                                                    )
                                                    archiveArtifacts artifacts: "${OWASP_DEP_REPORT}.*"
                                                    break

                                                case 'semgrep':
                                                    withCredentials([string(credentialsId: 'SEMGREP_KEY', variable: 'SEMGREP_KEY')]) {
                                                        sh "mkdir -p reports"
                                                        sh "semgrep --config=auto --sarif --output reports/semgrep.sarif ."
                                                        archiveArtifacts artifacts: "reports/semgrep.*"
                                                        recordIssues(
                                                            tool: sarif(
                                                                pattern: "reports/semgrep.sarif",
                                                                id: "SEMGREP-SAST",
                                                                name: "SAST Report"
                                                            )
                                                        )
                                                    }
                                                    break

                                                case 'checkov':
                                                    sh "checkov --directory . --output sarif"
                                                    recordIssues(
                                                        tool: sarif(
                                                            pattern: "${CHECKOV_REPORT}",
                                                            id: "Checkov-IaC",
                                                            name: "IAC Test Report"
                                                        )
                                                    )
                                                    archiveArtifacts artifacts: "${CHECKOV_REPORT}"
                                                    break
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
