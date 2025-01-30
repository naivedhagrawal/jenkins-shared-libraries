def call() {
  return """
        apiVersion: v1
        kind: Pod
        spec:
          containers:
          - name: zap
            image: zaproxy/zap-stable:latest
            securityContext:
              runAsUser: 1000
            volumeMounts:
            - name: zap-volume
              mountPath: /zap
            - name: home
              mountPath: /home/zap
            tty: true
          volumes:
          - name: zap-volume
            hostPath:
              path: /zap
              type: DirectoryOrCreate
          - name: home
            hostPath:
              path: /home/zap
              type: DirectoryOrCreate
"""
}