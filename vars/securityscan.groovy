/* @Library('k8s-shared-lib') _
securityscan(
    params: [
        GIT_URL: 'https://github.com/naivedhagrawal/devops_tools_kubernetes.git',
        GIT_BRANCH: 'main'
    ]
)
*/

import com.mycompany.utils.PodGenerator

def call(Map params = [:]) {
    String GIT_URL = ''
    String GIT_BRANCH = ''
    if (params instanceof Map) {
        def nestedParams = params['params'] ?: params
        GIT_URL = nestedParams['GIT_URL'] ?: ''
        GIT_BRANCH = nestedParams['GIT_BRANCH'] ?: ''
    } else {
        error "params is not a Map."
    }
    if (!GIT_URL || !GIT_BRANCH) {
        error "GIT_URL or GIT_BRANCH is not set!"
    }

    def GITLEAKS_REPORT = 'gitleaks-report'
    def OWASP_DEP_REPORT = 'owasp-dep-report'
    def SEMGREP_REPORT = 'semgrep-report'
    def CHECKOV_REPORT = 'results.sarif'
    def SEMGREP_CREDENTIALS_ID = 'SEMGREP_KEY'

    def containers = [
        [name: 'git', image: 'alpine/git:latest'],
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
            stage('Git Clone') {
                steps {
                    container('git') {
                        withEnv(["GIT_URL=${GIT_URL}", "GIT_BRANCH=${GIT_BRANCH}"]) {
                            sh '''
                                echo "Cloning repository from $GIT_URL - Branch: $GIT_BRANCH"
                                echo "Git version:"
                                git --version
                                echo "Cloning repository..."
                                git config --global --add safe.directory $PWD
                                git clone --depth=1 --branch $GIT_BRANCH $GIT_URL .
                            '''
                        }
                    }
                }
            }

            stage('Gitleak Check') {
                steps {
                    container('gitleak') {
                        sh "echo 'Gitleaks version:'"
                        sh "gitleaks --version"
                        sh "echo 'Running Gitleaks...'"
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
                                echo "Running OWASP Dependency Check..."
                                echo "OWASP Dependency Check version:"
                                /usr/share/dependency-check/bin/dependency-check.sh --version
                                echo "Scanning for vulnerabilities..."
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
                                sh "echo 'Semgrep version:'"
                                sh "semgrep --version"
                                sh "echo 'Running Semgrep...'"
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
                        sh "echo 'Checkov version:'"
                        sh "checkov --version"
                        sh "echo 'Running Checkov...'"
                        sh "checkov --directory . -o sarif -o csv || true"
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
