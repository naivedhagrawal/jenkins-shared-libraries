import com.mycompany.utils.PodGenerator

def call(Map params = [gitleak: true, owaspdependency: true, semgrep: true, checkov: true, GIT_URL: '', GIT_BRANCH: '']) {
    def GITLEAKS_REPORT = 'gitleaks-report'
    def OWASP_DEP_REPORT = 'owasp-dep-report'
    def SEMGREP_REPORT = 'semgrep-report'
    def CHECKOV_REPORT = 'results.sarif'
    def SEMGREP_CREDENTIALS_ID = 'SEMGREP_KEY'
    def GIT_URL = params.GIT_URL
    def GIT_BRANCH = params.GIT_BRANCH

    // Define the containers. Add git container for cloning the repository
    def containers = [
        [name: 'git', image: 'alpine/git:latest'],
        [name: 'gitleak', image: 'zricethezav/gitleaks'],
        [name: 'owasp', image: 'owasp/dependency-check-action:latest'],
        [name: 'semgrep', image: 'returntocorp/semgrep:latest'],
        [name: 'checkov', image: 'bridgecrew/checkov:latest']
    ]

    // Generate the pod YAML by calling the function from the shared library
    def podYaml = PodGenerator.generatePodYaml(containers)

    pipeline {
        agent {
            kubernetes {
                yaml podYaml
                showRawYaml true
            }
        }
        stages {
            stage('Git Clone') {
                steps {
                    container('git') {
                        sh '''
                            git init
                            git remote add origin ${GIT_URL}
                            git fetch --depth=1 origin ${GIT_BRANCH}
                            git checkout FETCH_HEAD
                        '''
                    }
                }
            }

            stage('Gitleak Check') {
                when { expression { params.gitleak } }
                steps {
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

            stage('OWASP Dependency Check') {
                when { expression { params.owaspdependency } }
                steps {
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

            stage('Semgrep Scan') {
                when { expression { params.semgrep } }
                steps {
                    withCredentials([string(credentialsId: SEMGREP_CREDENTIALS_ID, variable: 'SEMGREP_KEY')]) {
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

            stage('Checkov Scan') {
                when { expression { params.checkov } }
                steps {
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
