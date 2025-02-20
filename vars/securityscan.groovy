/* @Library('k8s-shared-lib') _
securityscan(
    gitleak: true,
    owaspdependency: true,
    semgrep: true,
    checkov: true,
)*/

def call(Map params = [gitleak: true, owaspdependency: true, semgrep: true, checkov: true]) {
    def GITLEAKS_REPORT = 'gitleaks-report.sarif'
    def OWASP_DEP_REPORT = 'owasp-dep-report.sarif'
    def SEMGREP_REPORT = 'semgrep-report.sarif'
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

            stage('OWASP Dependency Check') {
                when { expression { params.owaspdependency } }
                agent {
                    kubernetes {
                        yaml pod('owasp', 'owasp/dependency-check')
                        showRawYaml false
                    }
                }
                environment {
                        DC_PROJECT = "dependency-check scan: ${WORKSPACE}"
                        DATA_DIRECTORY = "${WORKSPACE}/OWASP-Dependency-Check/data"
                        CACHE_DIRECTORY = "${DATA_DIRECTORY}/cache"
                        REPORT_DIRECTORY = "${WORKSPACE}/reports"
                }
                steps {
                    script {
                        container('owasp') {
                                checkout scm
                                sh "mkdir -p ${DATA_DIRECTORY}"
                                sh "mkdir -p ${CACHE_DIRECTORY}"
                                sh "mkdir -p ${REPORT_DIRECTORY}"
                                sh '''
                                    dependency-check.sh \
                                        --scan ${WORKSPACE} \
                                        --format "ALL" \
                                        --project "$DC_PROJECT" \
                                        --out ${REPORT_DIRECTORY}
                                '''
                                recordIssues(
                                    enabledForFailure: true,
                                    tool: sarif(
                                        pattern: "${REPORT_DIRECTORY}/**",
                                        id: "Owasp-Dependency-Check",
                                        name: "OWASP Dependency Check Report"
                                    )
                                )
                                archiveArtifacts artifacts: "${REPORT_DIRECTORY}/**", fingerprint: true
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
