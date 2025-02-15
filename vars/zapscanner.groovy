def call() {
pipeline {
    parameters {
        string(name: 'TARGET_URL', defaultValue: 'https://google-gruyere.appspot.com/', description: 'Target URL for ZAP scan')
    }
    environment {
        ZAP_PROXY = "http://127.0.0.1:8080"
        // HTTP_PROXY and HTTPS_PROXY should NOT be set here for intra-pod ZAP communication
        // Set them at the pod level ONLY if your TARGET_URL needs a proxy
        // NO_PROXY also likely not needed for intra-pod communication
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
      ports:
        - containerPort: 8080
      securityContext:
        privileged: false
      resources:
        limits:
          cpu: "2"
          memory: "4Gi"
        requests:
          cpu: "1"
          memory: "2Gi"
  # Uncomment the following if your TARGET_URL needs a proxy
  #env:
  #  - name: HTTP_PROXY
  #    value: "http://your-external-proxy:port"
  #  - name: HTTPS_PROXY
  #    value: "http://your-external-proxy:port"
  #  - name: NO_PROXY
  #    value: "localhost,127.0.0.1"
'''
        }
    }
    stages {
        stage('Passive Scan') {
            steps {
                container('zap') {
                    sh """
                    set -e
                    ulimit -a
                    ulimit -u 100000 || true
                    ulimit -n 1048576 || true

                    zap.sh -daemon -host 0.0.0.0 -port 8080 &
                    ZAP_PID=\$!

                    trap "kill -9 \$ZAP_PID; exit 0" INT TERM EXIT

                    echo "Checking if ZAP is running..."
                    until curl --silent --head --fail "\$ZAP_PROXY"; do
                        echo "Waiting for ZAP daemon to start..."
                        sleep 5
                    done

                    echo "Using ZAP Proxy: \$ZAP_PROXY"

                    echo "Waiting for ZAP to be ready..."
                    for i in {1..30}; do
                        zap-cli --zap-url="\$ZAP_PROXY" status && break || sleep 2
                    done

                    echo "Opening target URL in ZAP..."
                    zap-cli --zap-url="\$ZAP_PROXY" open-url "\${TARGET_URL}" || { echo "Failed to open URL"; kill -9 \$ZAP_PID; exit 1; }

                    echo "Starting ZAP Spider Scan..."
                    zap-cli --zap-url="\$ZAP_PROXY" spider "\${TARGET_URL}" || { echo "Spider scan failed"; kill -9 \$ZAP_PID; exit 1; }

                    echo "Starting ZAP Active Scan..."
                    zap-cli --zap-url="\$ZAP_PROXY" active-scan "\${TARGET_URL}" || { echo "Active scan failed"; kill -9 \$ZAP_PID; exit 1; }

                    echo "Generating ZAP Report..."
                    zap-cli --zap-url="\$ZAP_PROXY" report -o zap_report.html -f html || { echo "Report generation failed"; kill -9 \$ZAP_PID; exit 1; }

                    trap - INT TERM EXIT
                    kill -9 \$ZAP_PID
                    echo "ZAP stopped."
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
