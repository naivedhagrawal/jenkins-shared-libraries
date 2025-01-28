def call() {
    return """
apiVersion: v1
kind: Pod
metadata:
  name: zap
spec:
  containers:
  - name: zap
    image: owasp/zap-stable  // Or owasp/zap-weekly
    ports:
    - containerPort: 8080 // ZAP API port
    - containerPort: 8090 // ZAP UI port (optional, for debugging)
    command: ["/zap.sh", "-daemon", "-port", "8080", "-host", "0.0.0.0"] // Start ZAP in daemon mode
    volumeMounts:
      - name: zap-volume
        mountPath: /zap/wrk/
  volumes:
  - name: zap-volume
    emptyDir: {}
"""
}
