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
            ports:
            - containerPort: 8080
            command: ["/bin/sh", "-c"]
            args:
            - |
              ZAP_HOME=\$(mktemp -d)
              /zap/zap.sh -daemon -host 0.0.0.0 -port 8080 -dir ${ZAP_HOME} & # Start ZAP
              timeout 60 sh -c 'while ! curl --fail http://localhost:8080/health; do sleep 5; done'
              tail -f /dev/null # Or some other long-running command
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
