def call() {
  return """
apiVersion: v1
kind: Pod
spec:
  containers:
  - name: zap-daemon
    image: zaproxy/zap-stable:latest
    command: ["/bin/sh", "-c"]
    args:
      - "zap.sh -daemon -host 0.0.0.0 -port 8080 && tail -f /dev/null"
    ports:
    - containerPort: 8080
    env:
    - name: ZAP_URL
      value: "http://localhost:8080"
    livenessProbe:
      httpGet:
        path: /
        port: 8080
      initialDelaySeconds: 10
      periodSeconds: 5
    readinessProbe:
      httpGet:
        path: /
        port: 8080
      initialDelaySeconds: 5
      periodSeconds: 3

  - name: zap
    image: naivedh/owasp-zap:latest
    command: ["/bin/sh", "-c", "export ZAP_URL=http://localhost:8080 && sleep infinity"]
    env:
    - name: ZAP_URL
      value: "http://localhost:8080"
    volumeMounts:
    - name: zap-data
      mountPath: /zap/reports
      subPath: reports

  volumes:
  - name: zap-data
    emptyDir: {}

  restartPolicy: Always
"""
}
