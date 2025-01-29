def call(String TARGET_URL = '') {
    return """apiVersion: v1
kind: Pod
spec:
  containers:
    - name: zap
      image: zaproxy/zap-stable:latest
      securityContext:
        runAsUser: 1000  # Assuming 'zap' user is mapped to UID 1000, adjust accordingly
      volumeMounts:
        - name: temp-volume
          mountPath: /zap/wrk  # Mounting /tmp on the host to /zap/wrk in the container
        - name: output-volume
          mountPath: /zap/output  # Mount an output directory
      tty: true  # Enable TTY for the container
  volumes:
    - name: temp-volume
      hostPath:
        path: /tmp
        type: Directory
    - name: output-volume
      hostPath:
        path: /tmp/zap-output  # Adjust this to where you want the output files stored
        type: Directory
"""
}
