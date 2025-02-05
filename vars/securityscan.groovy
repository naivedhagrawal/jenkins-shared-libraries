def call(Map params = [:]) {
    pipeline {
        agent none
        stages {
            stage('Gitleaks Scan') {
                when { expression { params.get('gitleak', true) } }
                steps {
                    echo 'Running Gitleaks scan...'
                    gitleakscan()
                }
            }

            stage('OWASP Dependency Check') {
                when { expression { params.get('owaspdependency', true) } }
                steps {
                    echo 'Running OWASP Dependency scan...'
                    owaspdependencycheck()
                }
            }

            stage('Semgrep Scan') {
                when { expression { params.get('semgrep', true) } }
                steps {
                    echo 'Running Semgrep scan...'
                    semgrepscan()
                }
            }
        }
    }
}
