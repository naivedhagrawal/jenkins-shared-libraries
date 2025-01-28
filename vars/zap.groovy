def call() {
    return """
apiVersion: v1
kind: Pod
metadata:
  name: zap
spec:
  containers:
  - name: zap
    image: zaproxy/zap-stable
    args: ["/zap.sh", "-daemon"]
"""
}
