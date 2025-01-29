def call(String TARGET_URL = '') {
    return """apiVersion: v1
kind: Pod
spec:
  containers:
    - name: zap
      image: zaproxy/zap-stable:latest
      command: ["zap-baseline.py"]
      args: ["-t", "${TARGET_URL}"]
      securityContext:
        runAsUser: 1000  # Assuming 'zap' user is mapped to UID 1000, adjust accordingly
      volumeMounts:
        - name: temp-volume
          mountPath: /zap/wrk
      tty: true  # Enable TTY for the container
  volumes:
    - name: temp-volume
      hostPath:
        path: /tmp
        type: Directory
"""
}
