def call() {
    return """
apiVersion: v1
kind: Pod
metadata:
  name: zap-pod
spec:
  containers:
    - name: zap
      image: naivedh/owasp-zap:latest
      command: ['cat']
      securityContext:
        runAsUser: 1000
        readOnlyRootFilesystem: false
        fsGroup: 1000
      volumeMounts:
        - name: zap-data
          mountPath: /zap/reports
          subPath: reports
          readOnly: false
        - name: zap-wrk
          mountPath: /zap/wrk
          subPath: wrk
          readOnly: false
        - name: zap-home
          mountPath: /home/zap/custom_data
          subPath: custom_data
          readOnly: false
      tty: true

  volumes:
    - name: zap-data
      emptyDir: {}
    - name: zap-home
      emptyDir: {}
    - name: zap-wrk
      persistentVolumeClaim:
        claimName: zap-pv-claim

  restartPolicy: Always

"""
}
