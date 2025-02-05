def call(Map params = [:]) {
    pipeline {
        agent none
        stages {
            stage('Gitleaks Scan') {
                when { expression { params.get('gitleak', true) } }
                steps {
                    script {
                        gitleakscan() // No extra ()
                    }
                }
            }

            stage('OWASP Dependency Check') {
                when { expression { params.get('owaspdependency', true) } }
                steps {
                    script {
                        owaspdependencycheck()
                    }
                }
            }

            stage('Semgrep Scan') {
                when { expression { params.get('semgrep', true) } }
                steps {
                    script {
                        semgrepscan()
                    }
                }
            }
        }
    }
}
