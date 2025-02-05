def call() {
    def SEMGREP_REPORT = 'semgrep-report.sarif'
    
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
