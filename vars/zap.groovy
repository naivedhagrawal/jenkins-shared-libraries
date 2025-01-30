def call() {
  return """
        apiVersion: v1
        kind: Pod
        metadata:
          name: zap
        spec:
          containers:
          - name: zap
            image: naivedh/owasp-zap:latest
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
            livenessProbe:
              httpGet:
                path: / 
                port: 8080
              initialDelaySeconds: 10
              periodSeconds: 30
              failureThreshold: 3
            readinessProbe:
              httpGet:
                path: / 
                port: 8080
              initialDelaySeconds: 5
              periodSeconds: 15
              failureThreshold: 3
            command:
              - "/bin/bash"
              - "-c"
              - "zap.sh -daemon -port 8080"
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
