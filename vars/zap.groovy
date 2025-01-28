def call() {
    return """
apiVersion: v1
kind: Pod
spec:
  containers:
  - name: zap
    image: zaproxy/zap-stable # Use a specific version!
    ports:
    - containerPort: 8080 # ZAP's default port
    imagePullPolicy: Always
    command:
      - cat
    tty: true
"""
}
