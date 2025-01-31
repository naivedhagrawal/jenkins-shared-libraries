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
      volumeMounts:
        - name: zap-wrk
          mountPath: /zap/wrk
          subPath: wrk
          readOnly: false
        - name: zap-wrk
          mountPath: /home/zap
          subPath: zap
          readOnly: false
      tty: true

  volumes:
    - name: zap-wrk
      persistentVolumeClaim:
        claimName: zap-pv-claim
"""
}
