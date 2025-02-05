def call() {
    def OWASP_DEP_REPORT = 'owasp-dep-report.sarif'
    
    stage('Owasp Dependency Check') {
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
