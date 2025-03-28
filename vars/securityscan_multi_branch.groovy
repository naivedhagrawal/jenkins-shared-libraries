/* @Library('k8s-shared-lib') _
securityscan(
    gitleak: true,
    owaspdependency: true,
    semgrep: true,
    checkov: true,
)*/

import com.mycompany.utils.PodGenerator

def call(Map params = [:]) {
    def GITLEAKS_REPORT = 'gitleaks-report'
    def OWASP_DEP_REPORT = 'owasp-dep-report'
    def SEMGREP_REPORT = 'semgrep-report'
    def CHECKOV_REPORT = 'results.sarif'

    def containers = [
        [name: 'gitleak', image: 'zricethezav/gitleaks:latest'],
        [name: 'owasp', image: 'owasp/dependency-check-action:latest'],
        [name: 'semgrep', image: 'returntocorp/semgrep:latest'],
        [name: 'checkov', image: 'bridgecrew/checkov:latest']
    ]

    def podYaml = PodGenerator.generatePodYaml(containers)

    pipeline {
        agent {
            kubernetes {
                yaml podYaml
                showRawYaml false
            }
        }

        stages {
            stage('Gitleak Check'){
                steps {
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

            stage('OWASP Dependency Check') {
                steps {
                        container('owasp') {
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
                                enabledForFailure: true,
                                tool: owaspDependencyCheck(
                                    pattern: "${OWASP_DEP_REPORT}.json",
                                    id: "Owasp-Dependency-Check",
                                    name: "Dependency Check Report"
                                )
                            )
                            archiveArtifacts artifacts: "${OWASP_DEP_REPORT}.*"
                        }
                }
            }


            stage('Semgrep Scan') {
                steps {
                        container('semgrep') {
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

            stage('Checkov Scan') {
                steps {
                        container('checkov') {
                            sh "checkov --directory . --output sarif || true"
                            recordIssues(
                                enabledForFailure: true,
                                tool: sarif(
                                    pattern: "${CHECKOV_REPORT}",
                                    id: "Checkov-IaC",
                                    name: "IAC Test Report"
                                )
                            )
                            archiveArtifacts artifacts: "${CHECKOV_REPORT}"
                        }
                }
            }
        }
    }
}
