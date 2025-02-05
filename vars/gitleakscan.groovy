def call() {
    def GITLEAKS_REPORT = 'gitleaks-report.sarif'
    
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
