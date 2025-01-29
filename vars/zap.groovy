def call() {
  return """apiVersion: v1
kind: Pod
spec:
  containers:
  - name: zap
    image: zaproxy/zap-stable:latest
    securityContext:
    runAsUser: 1000
    volumeMounts:
    - name: temp-volume
      mountPath: /zap/wrk
    - name: output-volume
      mountPath: /zap/output
    tty: true
  volumes:
  - name: temp-volume
    hostPath:
    path: /tmp
    type: Directory
  - name: output-volume
    hostPath:
    path: /tmp/zap-output
    type: Directory
"""
}
