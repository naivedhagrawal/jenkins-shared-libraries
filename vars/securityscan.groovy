/* @Library('k8s-shared-lib') _
securityscan(
    params: [
        gitleak: true,
        owaspdependency: true,
        semgrep: true,
        checkov: true,
        GIT_URL: 'https://github.com/naivedhagrawal/devops_tools_kubernetes.git',
        GIT_BRANCH: 'main'
    ]
)
*/

import com.mycompany.utils.PodGenerator

def call(Map params = [:]) {
    // Retrieve parameters with safe defaults, now also checking environment variables
    def GIT_URL = params.get('GIT_URL') ?: env.GIT_URL ?: ''
    def GIT_BRANCH = params.get('GIT_BRANCH') ?: env.GIT_BRANCH ?: ''

    if (!GIT_URL || !GIT_BRANCH) {
        error "🚨 GIT_URL or GIT_BRANCH is not set!  Provided values: GIT_URL=${GIT_URL}, GIT_BRANCH=${GIT_BRANCH}.  Please ensure these are set either as parameters to the 'securityscan' step or as environment variables in your Jenkins job configuration."
    }

    def GITLEAKS_REPORT = 'gitleaks-report'
    def OWASP_DEP_REPORT = 'owasp-dep-report'
    def SEMGREP_REPORT = 'semgrep-report'
    def CHECKOV_REPORT = 'results.sarif'
    def SEMGREP_CREDENTIALS_ID = 'SEMGREP_KEY'

    // Define the containers for the Kubernetes pod
    def containers = [
        [name: 'git', image: 'alpine/git:latest'],
        [name: 'gitleak', image: 'zricethezav/gitleaks:latest'],
        [name: 'owasp', image: 'owasp/dependency-check-action:latest'],
        [name: 'semgrep', image: 'returntocorp/semgrep:latest'],
        [name: 'checkov', image: 'bridgecrew/checkov:latest']
    ]

    // Generate the pod YAML from the shared library
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
                        withEnv(["GIT_URL=${GIT_URL}", "GIT_BRANCH=${GIT_BRANCH}"]) {
                            sh '''
                                echo "GIT_URL: $GIT_URL"
                                echo "GIT_BRANCH: $GIT_BRANCH"
                                echo "Cloning repository..."
                                git config --global --add safe.directory $PWD
                                git clone --depth=1 --branch $GIT_BRANCH $GIT_URL /source
                            '''
                        }
                    }
                }
            }

            stage('Gitleak Check') {
                when { expression { params.gitleak } }
                steps {
                    container('gitleak') {
                        sh '''
                            echo "Running Gitleaks scan..."
                            gitleaks detect --source=/source --report-path=/source/${GITLEAKS_REPORT}.sarif --report-format sarif --exit-code=0
                        '''
                        recordIssues(
                            enabledForFailure: true,
                            tool: sarif(
                                pattern: "/source/${GITLEAKS_REPORT}.sarif",
                                id: "Git-Leaks",
                                name: "Secret Scanning Report"
                            )
                        )
                        archiveArtifacts artifacts: "/source/${GITLEAKS_REPORT}.*"
                    }
                }
            }

            stage('OWASP Dependency Check') {
                when { expression { params.owaspdependency } }
                steps {
                    container('owasp') {
                        sh '''
                            echo "Running OWASP Dependency Check..."
                            mkdir -p /source/reports
                            /usr/share/dependency-check/bin/dependency-check.sh --scan /source \
                                --format "SARIF"  \
                                --exclude "**/*.zip" \
                                --out "/source/reports/"
                            
                            mv /source/reports/dependency-check-report.sarif /source/${OWASP_DEP_REPORT}.sarif
                        '''
                        recordIssues(
                            enabledForFailure: true,
                            tool: sarif(
                                pattern: "/source/${OWASP_DEP_REPORT}.sarif",
                                id: "Owasp-Dependency-Check",
                                name: "Dependency Check Report"
                            )
                        )
                        archiveArtifacts artifacts: "/source/${OWASP_DEP_REPORT}.*"
                    }
                }
            }

            stage('Semgrep Scan') {
                when { expression { params.semgrep } }
                steps {
                    container('semgrep') {
                        withCredentials([string(credentialsId: SEMGREP_CREDENTIALS_ID, variable: 'SEMGREP_KEY')]) {
                            sh '''
                                echo "Running Semgrep Scan..."
                                mkdir -p /source/reports
                                semgrep --config=auto --sarif --output /source/reports/semgrep.sarif /source || true
                            '''
                            recordIssues(
                                enabledForFailure: true,
                                tool: sarif(
                                    pattern: "/source/reports/semgrep.sarif",
                                    id: "SEMGREP-SAST",
                                    name: "SAST Report"
                                )
                            )
                            archiveArtifacts artifacts: "/source/reports/semgrep.*"
                        }
                    }
                }
            }

            stage('Checkov Scan') {
                when { expression { params.checkov } }
                steps {
                    container('checkov') {
                        sh '''
                            echo "Running Checkov Scan..."
                            checkov --directory /source --output sarif --output-file-path /source/${CHECKOV_REPORT} || true
                        '''
                        recordIssues(
                            enabledForFailure: true,
                            tool: sarif(
                                pattern: "/source/${CHECKOV_REPORT}",
                                id: "Checkov-IaC",
                                name: "IAC Test Report"
                            )
                        )
                        archiveArtifacts artifacts: "/source/${CHECKOV_REPORT}"
                    }
                }
            }
        }
    }
}
