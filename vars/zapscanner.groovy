pipeline {
    agent {
        kubernetes {
            yaml """
apiVersion: v1
kind: Pod
metadata:
  name: zap-pod
spec:
  containers:
  - name: zap
    image: naivedh/owasp-zap:latest
    command: ["/bin/sh", "-c"]
    args:
      - "zap.sh -daemon -host 0.0.0.0 -port 8080 \
         -config api.disablekey=true \
         -config api.addrs.addr.name=.* -config api.addrs.addr.regex=true && \
         for i in {1..30}; do curl -s http://localhost:8080 && break || sleep 5; done && \
         tail -f /zap/.ZAP_D/logs/zap.log"
    ports:
    - containerPort: 8080
    volumeMounts:
    - name: zap-data
      mountPath: /zap/wrk
  volumes:
  - name: zap-data
    emptyDir: {}
"""
        }
    }

    environment {
        ZAP_API_KEY = 'test-api-key' // You can set this dynamically
        TARGET_URL = 'http://example.com'
    }

    stages {
        stage('Start ZAP and Verify') {
            steps {
                container('zap') {
                    script {
                        echo 'Waiting for ZAP to start...'
                        sh 'curl -s http://localhost:8080'

                        echo 'Checking ZAP status...'
                        sh 'zap-cli --zap-url http://localhost:8080 status'
                    }
                }
            }
        }

        stage('Run ZAP Scan') {
            steps {
                container('zap') {
                    script {
                        echo "Starting ZAP scan on ${TARGET_URL}"
                        sh "zap-cli --zap-url http://localhost:8080 quick-scan ${TARGET_URL}"
                    }
                }
            }
        }

        stage('Archive Reports') {
            steps {
                archiveArtifacts artifacts: 'zap-out.html'
            }
        }
    }
}
