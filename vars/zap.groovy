def call() {
    return """
apiVersion: v1
kind: Pod
spec:
    containers:
    - name: zap-daemon
            image: zaproxy/zap-bare:latest
            command: ["/zap/zap.sh", "-daemon", "-host", "127.0.0.1", "-port", "8080"]
            securityContext:
              runAsUser: 1000
              readOnlyRootFilesystem: false
              fsGroup: 1000
            volumeMounts:
            - name: zap-home
              mountPath: /home/zap/custom_data
              subPath: custom_data
              readOnly: false
            tty: true
          - name: zap
            image: zaproxy/zap-bare:latest
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
              mountPath: /zap/wrk/data
              subPath: data
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
            emptyDir: {}
    restartPolicy: Always
"""
}
