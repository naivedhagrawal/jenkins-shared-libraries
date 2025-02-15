def call() {
pipeline {
    parameters {
        string(name: 'TARGET_URL', defaultValue: 'https://example.com', description: 'Target URL for ZAP scan')
    }
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
'''
        }
    }
    stages {
        stage('Passive Scan') {
            steps {
                container('zap') {
                    sh """
                    ulimit -a
                    ulimit -u 100000 || true
                    ulimit -n 1048576 || true
                    
                    zap.sh -daemon -host 0.0.0.0 -port 8080 &
                    sleep 30
                    
                    # Health check for ZAP daemon using curl
                    if ! curl --silent --head --fail http://localhost:8080; then
                        echo "ZAP daemon failed to start"
                        exit 1
                    fi
                    
                    zap-cli --zap-url http://localhost:8080 open-url ${TARGET_URL}
                    zap-cli --zap-url http://localhost:8080 spider ${TARGET_URL}
                    zap-cli --zap-url http://localhost:8080 active-scan ${TARGET_URL}
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
