def call(String TARGET_URL = '') {
    return """
apiVersion: v1
kind: Pod
metadata:
  name: zaproxy-pod
spec:
  containers:
    - name: zaproxy
      image: zaproxy/zap-stable:latest
      command: ["zap-baseline.py"]
      args: ["-t", "${TARGET_URL}"]
      securityContext:
        runAsUser: 1000  # Assuming 'zap' user is mapped to UID 1000, adjust accordingly
      volumeMounts:
        - name: temp-volume
          mountPath: /zap/wrk
  volumes:
    - name: temp-volume
      hostPath:
        path: /tmp
        type: Directory


    """
}