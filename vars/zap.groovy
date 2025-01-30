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
      mountPath: /zap
    - name: output-volume
      mountPath: /home/zap
    tty: true
  volumes:
  - name: temp-volume
    emptyDir: {}
  - name: output-volume
    emptyDir: {}
"""
}
