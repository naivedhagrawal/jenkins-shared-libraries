import com.mycompany.utils.PodGenerator

def call(String GIT_URL, String GIT_BRANCH) {
    def GITLEAKS_REPORT = 'gitleaks-report'
    def OWASP_DEP_REPORT = 'owasp-dep-report'
    def SEMGREP_REPORT = 'semgrep-report'
    def CHECKOV_REPORT = 'results.sarif'
    def SEMGREP_CREDENTIALS_ID = 'semgrep-key'



    // Define the containers.  Include git, but remove the explicit declaration.
    def containers = [
        [name: 'gitleak', image: 'zricethezav/gitleaks'],
        [name: 'owasp', image: 'owasp/dependency-check-action:latest'],
        [name: 'semgrep', image: 'returntocorp/semgrep:latest'],
        [name: 'checkov', image: 'bridgecrew/checkov:latest']
    ]

    // Generate the pod YAML by calling the function from the shared library
    def podYaml = PodGenerator.generatePodYaml(containers, GIT_URL, GIT_BRANCH)

    pipeline {
        agent {
            kubernetes {
                yaml podYaml
                showRawYaml true
            }
        }
        stages {
            stage('Gitleak Check') {
                steps {
                    script {
                        container('gitleak') {
                            sh "gitleaks detect --source=/source --report-path=${GITLEAKS_REPORT}.sarif --report-format sarif --exit-code=0"
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
                steps {
                    script {
                        container('owasp') {
                            sh """
                                mkdir -p reports
                                /usr/share/dependency-check/bin/dependency-check.sh --scan /source \
                                    --format "SARIF"  \
                                    --exclude "**/*.zip" \
                                    --out "reports/"
                                
                                mv reports/dependency-check-report.sarif ${OWASP_DEP_REPORT}.sarif
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

            stage('Semgrep Scan') {
                steps {
                    script {
                        withCredentials([string(credentialsId: SEMGREP_CREDENTIALS_ID, variable: 'SEMGREP_KEY')]) {
                            container('semgrep') {
                                sh "mkdir -p reports"
                                sh "semgrep --config=auto --sarif --output reports/semgrep.sarif /source"
                                recordIssues(
                                    enabledForFailure: true,
                                    tool: sarif(
                                        pattern: "reports/semgrep.sarif",
                                        id: "SEMGREP-SAST",
                                        name: "SAST Report"
                                    )
                                )
                                archiveArtifacts artifacts: "reports/semgrep.*"
                            }
                        }
                    }
                }
            }

            stage('Checkov Scan') {
                steps {
                    script {
                        container('checkov') {
                            sh "checkov --directory /source --output sarif || true"
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
}
