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
    command: [ "/bin/sh", "-c", "zap.sh -daemon -host 0.0.0.0 -port 8080" ]
    env:
    - name: JAVA_OPTS
      value: "-XX:ThreadStackSize=512"
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
    ports:
    - containerPort: 8080
      protocol: TCP
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
