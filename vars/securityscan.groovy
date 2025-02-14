/* @Library('k8s-shared-lib') _
securityscan(
    gitleak: true,
    owaspdependency: true,
    semgrep: true,
    checkov: true,
    detect_secrets: true
)*/

def call(Map params = [gitleak: true, owaspdependency: true, semgrep: true, checkov: true]) {
    def GITLEAKS_REPORT = 'gitleaks-report.sarif'
    def OWASP_DEP_REPORT = 'owasp-dep-report.sarif'
    def SEMGREP_REPORT = 'semgrep-report.sarif'
    def CHECKOV_REPORT = 'results.sarif'
    def DETECT_SECRETS = 'detect-secrets-report.sarif'

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
                            sh """
                                gitleaks detect \
                                    --source=. \
                                    --report-path=${GITLEAKS_REPORT} \
                                    --report-format sarif \
                                    --exit-code=0
                            """
                            recordIssues(
                                enabledForFailure: true,
                                tool: sarif(
                                    pattern: "${GITLEAKS_REPORT}",
                                    id: "Git-Leaks",
                                    name: "Gitleak Report"
                                )
                            )
                            archiveArtifacts artifacts: "${GITLEAKS_REPORT}"
                        }
                    }
                }
            }

            stage('Detect Secrets') {
                when { expression { params.detect_secrets } }
                agent {
                    kubernetes {
                        yaml pod('detect-secrets', 'python:latest')
                        showRawYaml false
                    }
                }
                steps {
                    script {
                        container('detect-secrets') {
                            checkout scm
                            sh '''
                                pip install detect-secrets --quiet
                                detect-secrets scan --all-files | jq '.' > detect-secrets-report.sarif

                                if grep -q '"is_secret": true' detect-secrets-report.sarif; then
                                    echo "❌ Secrets detected! Failing build."
                                    exit 1
                                else
                                    echo "✅ No secrets found. Proceeding with build."
                                fi
                                '''
                            recordIssues(
                                enabledForFailure: true,
                                tool: sarif(
                                    pattern: "${DETECT_SECRETS}",
                                    id: "Detect-Secrets",
                                    name: "Detect Secrets Report"
                                )
                            )
                            archiveArtifacts artifacts: "${DETECT_SECRETS}"
                        }
                    }
                }
            }

            stage('OWASP Dependency Check') {
                when { expression { params.owaspdependency } }
                agent {
                    kubernetes {
                        yaml pod('owasp', 'naivedh/owasp-dependency:latest')
                        showRawYaml false
                    }
                }
                steps {
                    script {
                        container('owasp') {
                            withCredentials([string(credentialsId: 'NVD_API_KEY', variable: 'NVD_API_KEY')]) {
                                checkout scm
                                sh """
                                    dependency-check --scan . \
                                        --format SARIF \
                                        --exclude "**/*.zip" \
                                        --out ${OWASP_DEP_REPORT} \
                                        --nvdApiKey ${env.NVD_API_KEY}
                                """
                                recordIssues(
                                    enabledForFailure: true,
                                    tool: sarif(
                                        pattern: "${OWASP_DEP_REPORT}",
                                        id: "Owasp-Dependency-Check",
                                        name: "OWASP Dependency Check Report"
                                    )
                                )
                                archiveArtifacts artifacts: "${OWASP_DEP_REPORT}"
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
                                sh "SEMGREP_APP_TOKEN=${SEMGREP_KEY} semgrep login"
                                sh "semgrep --config=auto --sarif --output ${SEMGREP_REPORT} ."
                                archiveArtifacts artifacts: "${SEMGREP_REPORT}"
                                recordIssues(
                                    enabledForFailure: true,
                                    tool: sarif(
                                        pattern: "${SEMGREP_REPORT}",
                                        id: "SEMGREP-SAST",
                                        name: "Semgrep Report"
                                    )
                                )
                            }
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
                            sh """
                                checkov --directory . --output sarif || true
                                ls -lrt
                            """
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
