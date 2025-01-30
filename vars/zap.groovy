def call() {
  return """
        apiVersion: v1
        kind: Pod
        metadata:
          name: zap
        spec:
          containers:
          - name: zap
            image: zaproxy/zap-stable:latest
            securityContext:
              runAsUser: 1000
              readOnlyRootFilesystem: false
            volumeMounts:
            - name: zap-data
              mountPath: /zap/reports
              subPath: reports
            - name: zap-home
              mountPath: /home/zap/custom_data  # `/home/zap` को प्रभावित किए बिना अलग डायरेक्टरी माउंट करें
              subPath: custom_data
            tty: true
          volumes:
          - name: zap-data
            emptyDir: {}
          - name: zap-home
            emptyDir: {}  # या PVC के लिए PersistentVolumeClaim उपयोग करें
          restartPolicy: Always

"""
}