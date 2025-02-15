def call() {
pipeline {
    agent {
        kubernetes {
            yaml '''
apiVersion: v1
kind: Pod
metadata:
  name: zap-agent
  labels:
    jenkins-agent: zap
spec:
  containers:
    - name: zap
      image: zaproxy/zap-stable
      command: ["/bin/sh", "-c"]
      args: ["cat"]
      tty: true
      securityContext:
        privileged: true
      resources:
        limits:
          memory: "2Gi"
          cpu: "1000m"
        requests:
          memory: "512Mi"
          cpu: "500m"
'''
        }
    }
    stages {
        stage('Passive Scan') {
            steps {
                container('zap') {
                    sh """
                    zap.sh -daemon -host 0.0.0.0 -port 8080 &
                    sleep 10
                    zap-cli --zap-url http://localhost:8080 open-url https://example.com
                    zap-cli --zap-url http://localhost:8080 spider https://example.com
                    zap-cli --zap-url http://localhost:8080 active-scan https://example.com
                    zap-cli --zap-url http://localhost:8080 report -o zap_report.html -f html
                    """
                }
            }
            post {
                always {
                    archiveArtifacts artifacts: 'zap_report.html', fingerprint: true
                }
            }
        }
    }
}

}
