def call() {
    return """
apiVersion: v1
kind: Pod
spec:
  containers:
  - name: zap
    image: owasp/zap-stable:latest # Use a specific version!
    ports:
    - containerPort: 8080 # ZAP's default port
"""
}
