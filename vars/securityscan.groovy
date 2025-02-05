def call(Boolean gitleak = true, Boolean owaspdependency = true, Boolean semgrep = true) {
    
    pipeline {
        agent any
        
        stages {
            stage('Gitleaks Scan') {
                when { expression { return gitleak } }
                steps {
                    echo 'Running Gitleaks scan...'
                    gitleakscan()()
                }
            }
            
            stage('OWASP Dependency Check') {
                when { expression { return owaspdependency } }
                steps {
                    echo 'Running OWASP Dependency scan...'
                    owaspdependencycheck()()
                }
            }
            
            stage('Semgrep Scan') {
                when { expression { return semgrep } }
                steps {
                    echo 'Running Semgrep scan...'
                    semgrepscan()()
                }
            }
        }
    }
}