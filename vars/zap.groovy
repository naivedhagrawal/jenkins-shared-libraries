def call() {
    return """
apiVersion: v1
kind: Pod
spec:
  containers:
    - name: zap
      image: zaproxy/zap-stable
      command: ["/bin/sh", "-c", "tail -f /dev/null"]
      securityContext:
        runAsUser: 1000
        runAsGroup: 1000
        readOnlyRootFilesystem: false
        fsGroup: 1000
      tty: true
"""
}
