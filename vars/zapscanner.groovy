def call() {
pipeline {
    parameters {
        string(name: 'TARGET_URL', defaultValue: 'https://google-gruyere.appspot.com/', description: 'Target URL for ZAP scan')
    }
    environment {
        ZAP_PROXY = "http://127.0.0.1:8080"
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
      image: naivedh/owasp-zap:latest
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
                    if ! curl --silent --head --fail "$ZAP_PROXY"; then
                        echo "ZAP daemon failed to start"
                        exit 1
                    fi
                    
                    echo "Using ZAP Proxy: $ZAP_PROXY"
                    
                    # Wait for ZAP to be fully initialized
                    echo "Waiting for ZAP to be ready..."
                    for i in {1..30}; do
                        if zap-cli --zap-url="$ZAP_PROXY" status; then
                            break
                        fi
                        sleep 2
                    done
                    
                    # Ensure correct proxy format
                    export HTTP_PROXY="$ZAP_PROXY"
                    export HTTPS_PROXY="$ZAP_PROXY"
                    export no_proxy="localhost,127.0.0.1"
                    
                    zap-cli --zap-url="$ZAP_PROXY" open-url "${TARGET_URL}"
                    zap-cli --zap-url="$ZAP_PROXY" spider "${TARGET_URL}"
                    zap-cli --zap-url="$ZAP_PROXY" active-scan "${TARGET_URL}"
                    zap-cli --zap-url="$ZAP_PROXY" report -o zap_report.html -f html
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
