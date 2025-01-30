def call() {
  return """
        apiVersion: v1
        kind: Pod
        metadata:
          name: zap
          labels:
            app: zap
        spec:
          containers:
          - name: zap
            image: naivedh/owasp-zap:latest
            command: ["/zap/zap.sh", "-daemon", "-host", "127.0.0.1", "-port", "8080"]
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
            livenessProbe:
              httpGet:
                path: /
                port: 8080
                host: 127.0.0.1
              initialDelaySeconds: 10
              periodSeconds: 5
            readinessProbe:
              httpGet:
                path: /
                port: 8080
                host: 127.0.0.1
              initialDelaySeconds: 5
              periodSeconds: 5
            tty: true
          volumes:
          - name: zap-data
            emptyDir: {}
          - name: zap-home
            emptyDir: {}
          - name: zap-wrk
            emptyDir: {}
          restartPolicy: Always
        ---
        apiVersion: v1
        kind: Service
        metadata:
          name: zap-service
        spec:
          selector:
            app: zap
          ports:
          - protocol: TCP
            port: 8080 
            targetPort: 8080 
          type: NodePort
  """
}
