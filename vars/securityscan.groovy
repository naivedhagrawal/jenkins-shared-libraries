def call(Boolean gitleak = true, Boolean owaspdependency = true, Boolean semgrep = true) {
    return {
        if (gitleak) {
            stage('Gitleaks Scan') {
                steps {
                    echo 'Running Gitleaks scan...'
                    gitleakscan()()
                }
            }
        }

        if (owaspdependency) {
            stage('OWASP Dependency Check') {
                steps {
                    echo 'Running OWASP Dependency scan...'
                    owaspdependencycheck()()
                }
            }
        }

        if (semgrep) {
            stage('Semgrep Scan') {
                steps {
                    echo 'Running Semgrep scan...'
                    semgrepscan()()
                }
            }
        }
    }
}
