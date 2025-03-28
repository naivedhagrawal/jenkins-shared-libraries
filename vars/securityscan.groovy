/* @Library('k8s-shared-lib') _
securityscan(
    gitleak: true,
    owaspdependency: true,
    semgrep: true,
    checkov: true,
    sonarqube: true,
)*/

def call(Map params = [gitleak: true, owaspdependency: true, semgrep: true, checkov: true, sonarqube: true]) {
    def GITLEAKS_REPORT = 'gitleaks-report'
    def OWASP_DEP_REPORT = 'owasp-dep-report'
    def SEMGREP_REPORT = 'semgrep-report'
    def CHECKOV_REPORT = 'results.sarif'
    def WORKSPACE_DIR = '/workspace'  // Shared workspace path

    pipeline {
        agent none

        stages {
            stage('Checkout') {
                agent {
                    kubernetes {
                        yaml """
apiVersion: v1
kind: Pod
spec:
  containers:
    - name: checkout
      image: jenkins/inbound-agent:latest
      command:
        - cat
      tty: true
      volumeMounts:
        - name: workspace-volume
          mountPath: ${WORKSPACE_DIR}
  volumes:
    - name: workspace-volume
      persistentVolumeClaim:
        claimName: jenkins-workspace-pvc
"""
                        defaultContainer 'checkout'
                    }
                }
                steps {
                    dir(WORKSPACE_DIR) {
                        checkout scm
                    }
                }
            }

            stage('Gitleak Check') {
                when { expression { params.gitleak } }
                agent {
                    kubernetes {
                        yaml """
apiVersion: v1
kind: Pod
spec:
  containers:
    - name: gitleak
      image: zricethezav/gitleaks
      command:
        - cat
      tty: true
      volumeMounts:
        - name: workspace-volume
          mountPath: ${WORKSPACE_DIR}
  volumes:
    - name: workspace-volume
      persistentVolumeClaim:
        claimName: jenkins-workspace-pvc
"""
                        defaultContainer 'gitleak'
                    }
                }
                steps {
                    dir(WORKSPACE_DIR) {
                        sh "gitleaks detect --source=. --report-path=${GITLEAKS_REPORT}.sarif --report-format sarif --exit-code=0"
                        recordIssues(
                            enabledForFailure: true,
                            tool: sarif(
                                pattern: "${GITLEAKS_REPORT}.sarif",
                                id: "Git-Leaks",
                                name: "Secret Scanning Report"
                            )
                        )
                        archiveArtifacts artifacts: "${GITLEAKS_REPORT}.*"
                    }
                }
            }

            stage('OWASP Dependency Check') {
                when { expression { params.owaspdependency } }
                agent {
                    kubernetes {
                        yaml """
apiVersion: v1
kind: Pod
spec:
  containers:
    - name: owasp
      image: owasp/dependency-check-action:latest
      command:
        - cat
      tty: true
      volumeMounts:
        - name: workspace-volume
          mountPath: ${WORKSPACE_DIR}
  volumes:
    - name: workspace-volume
      persistentVolumeClaim:
        claimName: jenkins-workspace-pvc
"""
                        defaultContainer 'owasp'
                    }
                }
                steps {
                    dir(WORKSPACE_DIR) {
                        sh """
                            mkdir -p reports
                            /usr/share/dependency-check/bin/dependency-check.sh --scan . \
                                --format "SARIF" \
                                --format "JSON" \
                                --format "CSV" \
                                --format "XML" \
                                --exclude "**/*.zip" \
                                --out "reports/"
                            
                            mv reports/dependency-check-report.sarif ${OWASP_DEP_REPORT}.sarif
                            mv reports/dependency-check-report.json ${OWASP_DEP_REPORT}.json
                            mv reports/dependency-check-report.csv ${OWASP_DEP_REPORT}.csv
                            mv reports/dependency-check-report.xml ${OWASP_DEP_REPORT}.xml
                        """
                        recordIssues(
                            enabledForFailure: true,
                            tool: owaspDependencyCheck(
                                pattern: "${OWASP_DEP_REPORT}.json",
                                id: "Owasp-Dependency-Check",
                                name: "Dependency Check Report"
                            )
                        )
                        archiveArtifacts artifacts: "${OWASP_DEP_REPORT}.*"
                    }
                }
            }

            stage('Semgrep Scan') {
                when { expression { params.semgrep } }
                agent {
                    kubernetes {
                        yaml """
apiVersion: v1
kind: Pod
spec:
  containers:
    - name: semgrep
      image: returntocorp/semgrep:latest
      command:
        - cat
      tty: true
      volumeMounts:
        - name: workspace-volume
          mountPath: ${WORKSPACE_DIR}
  volumes:
    - name: workspace-volume
      persistentVolumeClaim:
        claimName: jenkins-workspace-pvc
"""
                        defaultContainer 'semgrep'
                    }
                }
                steps {
                    dir(WORKSPACE_DIR) {
                        withCredentials([string(credentialsId: 'SEMGREP_KEY', variable: 'SEMGREP_KEY')]) {
                            sh "mkdir -p reports"
                            sh "semgrep --config=auto --sarif --output reports/semgrep.sarif ."
                            archiveArtifacts artifacts: "reports/semgrep.*"
                            recordIssues(
                                enabledForFailure: true,
                                tool: sarif(
                                    pattern: "reports/semgrep.sarif",
                                    id: "SEMGREP-SAST",
                                    name: "SAST Report"
                                )
                            )
                        }
                    }
                }
            }

            stage('Checkov Scan') {
                when { expression { params.checkov } }
                agent {
                    kubernetes {
                        yaml """
apiVersion: v1
kind: Pod
spec:
  containers:
    - name: checkov
      image: bridgecrew/checkov:latest
      command:
        - cat
      tty: true
      volumeMounts:
        - name: workspace-volume
          mountPath: ${WORKSPACE_DIR}
  volumes:
    - name: workspace-volume
      persistentVolumeClaim:
        claimName: jenkins-workspace-pvc
"""
                        defaultContainer 'checkov'
                    }
                }
                steps {
                    dir(WORKSPACE_DIR) {
                        sh "checkov --directory . --output sarif || true"
                        recordIssues(
                            enabledForFailure: true,
                            tool: sarif(
                                pattern: "${CHECKOV_REPORT}",
                                id: "Checkov-IaC",
                                name: "IAC Test Report"
                            )
                        )
                        archiveArtifacts artifacts: "${CHECKOV_REPORT}"
                    }
                }
            }
        }
    }
}
