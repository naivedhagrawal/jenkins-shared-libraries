def call() {
  return """
apiVersion: v1
kind: Pod
metadata:
  name: zap-pod
spec:
  containers:
  - name: zap-daemon
    image: naivedh/owasp-zap:latest
    command: ["/bin/sh", "-c", "zap.sh -daemon -host 0.0.0.0 -port 8080 & sleep infinity"]
    env:
    - name: JAVA_OPTS
      value: "-XX:ThreadStackSize=512"
    securityContext:
      runAsUser: 1000
    volumeMounts:
    - name: zap-data
      mountPath: /zap/reports
      subPath: reports
    - name: zap-wrk
      mountPath: /zap/wrk/data
      subPath: data
    - name: zap-home
      mountPath: /home/zap/custom_data
      subPath: custom_data

  - name: zap
    image: naivedh/owasp-zap:latest
    command: ["/bin/sh", "-c", "sleep infinity"]
    env:
    - name: ZAP_URL
      value: "http://localhost:8080"
    volumeMounts:
    - name: zap-data
      mountPath: /zap/reports
      subPath: reports
    - name: zap-wrk
      mountPath: /zap/wrk/data
      subPath: data
    - name: zap-home
      mountPath: /home/zap/custom_data
      subPath: custom_data

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
