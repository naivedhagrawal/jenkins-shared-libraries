def call() {
    return """
apiVersion: v1
kind: Pod
metadata:
  name: zap-pod
spec:
  containers:
  - name: zap
    image: zaproxy/zap-stable  # Correct image name
    ports:
    - containerPort: 8080 # ZAP API port
    - containerPort: 8090 # ZAP UI port (optional)
    args: ["/zap.sh", "-daemon", "-port", "8080", "-host", "0.0.0.0"]
    volumeMounts: # If you need persistent storage for ZAP data
      - name: zap-volume
        mountPath: /zap/wrk/
  volumes:
  - name: zap-volume
    emptyDir: {}
"""
}
