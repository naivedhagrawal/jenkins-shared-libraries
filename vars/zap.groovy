def call() {
  return """
        apiVersion: v1
        kind: Pod
        metadata:
          name: zap
        spec:
          initContainers:
          - name: init-chown
            image: busybox
            command: ["sh", "-c", "mkdir -p /zap/wrk && touch /zap/wrk/zap.yaml && chown -R 1000:1000 /zap"]
            volumeMounts:
            - name: zap-data
              mountPath: /zap/reports
            - name: zap-wrk
              mountPath: /zap/wrk
            - name: zap-home
              mountPath: /home/zap
          containers:
          - name: zap
            image: zaproxy/zap-stable:latest
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
            emptyDir: {}
          restartPolicy: Always
  """
}
