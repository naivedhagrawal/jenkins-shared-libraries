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
    env:
      - name: ZAP_PATH
        value: "/zap/zap.sh"
      - name: HOME
        value: "/home/zap"
"""
}
