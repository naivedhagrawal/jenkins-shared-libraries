/* @Library('k8s-shared-lib') _
securityscan(
    gitleak: true,
    owaspdependency: true,
    semgrep: true,
    checkov: true
)*/

def call(Map params = [gitleak: true, owaspdependency: true, semgrep: true, checkov: true]) {
    def GITLEAKS_REPORT = 'gitleaks-report.sarif'
    def OWASP_DEP_REPORT = 'owasp-dep-report.sarif'
    def SEMGREP_REPORT = 'semgrep-report.sarif'
    def CHECKOV_REPORT = './checkov-report.sarif'

    pipeline {
        agent none

        stages {
            stage('Gitleak Check') {
                when {
                    expression { params.gitleak }
                }
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
                                    id: "gitLeaks-SARIF",
                                    name: "Gitleak Report"
                                )
                            )
                            archiveArtifacts artifacts: "${GITLEAKS_REPORT}"
                        }
                    }
                }
            }

            stage('OWASP Dependency Check') {
                when {
                    expression { params.owaspdependency }
                }
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
                                    /usr/share/dependency-check/bin/dependency-check.sh --scan . \
                                        --format SARIF \
                                        --exclude "**/*.zip" \
                                        --out ${OWASP_DEP_REPORT} \
                                        --nvdApiKey ${env.NVD_API_KEY}
                                """
                                recordIssues(
                                    enabledForFailure: true,
                                    tool: sarif(
                                        pattern: "${OWASP_DEP_REPORT}",
                                        id: "owasp-dependency-check-SARIF",
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
                when {
                    expression { params.semgrep }
                }
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
                            sh """
                                semgrep --config=auto --sarif --output ${SEMGREP_REPORT} .
                            """
                            recordIssues(
                                enabledForFailure: true,
                                tool: sarif(
                                    pattern: "${SEMGREP_REPORT}",
                                    id: "semgrep-SARIF",
                                    name: "Semgrep Report"
                                )
                            )
                            archiveArtifacts artifacts: "${SEMGREP_REPORT}"
                        }
                    }
                }
            }

            stage('Checkov Scan') {
                when {
                    expression { params.checkov }
                }
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
                                checkov --directory . --output sarif --output-file-path ${CHECKOV_REPORT}
                            """
                            recordIssues(
                                enabledForFailure: true,
                                tool: sarif(
                                    pattern: "${CHECKOV_REPORT}",
                                    id: "checkov-SARIF",
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
