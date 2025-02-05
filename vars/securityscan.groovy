def call(Map params = [:]) {
    def GITLEAKS_REPORT = 'gitleaks-report.sarif'
    def OWASP_DEP_REPORT = 'owasp-dep-report.sarif'
    def SEMGREP_REPORT = 'semgrep-report.sarif'

    if (params.gitleak) {
        stage('Gitleak Check') {
            agent {
                kubernetes {
                    yaml pod('gitleak', 'zricethezav/gitleaks')
                    showRawYaml false
                }
            }
            steps {
                container('gitleak') {
                    sh """
                        gitleaks detect \
                            --source=. \
                            --report-path=${env.GITLEAKS_REPORT} \
                            --report-format sarif \
                            --exit-code=0
                    """
                    recordIssues(
                        enabledForFailure: true,
                        tool: sarif(
                            pattern: "${env.GITLEAKS_REPORT}",
                            id: "gitLeaks-SARIF",
                            name: "GitLeaks-Report"
                        )
                    )
                    archiveArtifacts artifacts: "${env.GITLEAKS_REPORT}"
                }
            }
        }
    }

    if (params.owaspdependency) {
        stage('OWASP Dependency Check') {
            agent {
                kubernetes {
                yaml pod('owasp', 'naivedh/owasp-dependency:latest')
                showRawYaml false
                }
            }
            steps {
            container('owasp') {
                withCredentials([string(credentialsId: 'NVD_API_KEY', variable: 'NVD_API_KEY')]) {
                    sh """
                        dependency-check --scan . \
                            --format SARIF \
                            --out ${OWASP_DEP_REPORT} \
                            --nvdApiKey ${env.NVD_API_KEY}
                    """
                    
                    recordIssues(
                        enabledForFailure: true,
                        tool: sarif(
                            pattern: "${OWASP_DEP_REPORT}",
                            id: "owasp-dependency-check-SARIF",
                            name: "owasp-dependency-check-Report"
                        )
                    )
                    
                    archiveArtifacts artifacts: "${OWASP_DEP_REPORT}"
                }
            }
        }
        }
    }

    if (params.semgrep) {
        stage('Semgrep Scan') {
           agent {
                kubernetes {
                    yaml pod('semgrep','returntocorp/semgrep')
                    showRawYaml false
                }
            }
            steps {
                container('semgrep') {
                    sh """
                    semgrep --config=auto --sarif --output ${env.SEMGREP_REPORT} .
                    """
                    recordIssues(
                            enabledForFailure: true,
                            tool: sarif(pattern: "${env.SEMGREP_REPORT}", id: "semgrep-SARIF", name: "semgrep-Report") )
                    archiveArtifacts artifacts: "${env.SEMGREP_REPORT}"
                }
            }
        }
    }
}
