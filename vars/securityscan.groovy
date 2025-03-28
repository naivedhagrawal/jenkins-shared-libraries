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
                steps {
                    container('gitleak') {
                        sh '''
                            cd /source
                            gitleaks detect --source=. --report-path=${GITLEAKS_REPORT}.sarif --report-format sarif --exit-code=0
                            ls -lh
                            cd ..
                            ls -lh
                        '''
                        recordIssues(
                            enabledForFailure: true,
                            tool: sarif(
                                pattern: "/source/${GITLEAKS_REPORT}.sarif",
                                id: "Git-Leaks",
                                name: "Secret Scanning Report"
                            )
                        )
                        // Updated artifact path to match the filename
                        archiveArtifacts artifacts: "/source/${GITLEAKS_REPORT}.sarif"
                    }
                }
            }
            stage('OWASP Dependency Check') {
                steps {
                    container('owasp') {
                        sh '''
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
