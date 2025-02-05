def call(Map params = [:]) {
    Boolean gitleak = params.get('gitleak', true)
    Boolean owaspdependency = params.get('owaspdependency', true)
    Boolean semgrep = params.get('semgrep', true)

    return {
        stage('Security Scans') {
            parallel {
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
    }
}
