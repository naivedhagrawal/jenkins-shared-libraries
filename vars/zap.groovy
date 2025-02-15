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
    command: ["/bin/sh", "-c"]
    args:
      - "export JVM_ARGS='-Xmx6g' && zap.sh -daemon -host 0.0.0.0 -port 8080 -config api.addrs.addr.name=.* -config api.addrs.addr.regex=true -config api.disablekey=true && tail -f /dev/null"
    ports:
    - containerPort: 8080
    env:
    - name: ZAP_URL
      value: "http://localhost:8080"
    resources:
      limits:
        memory: "8Gi"
        cpu: "4"
      requests:
        memory: "4Gi"
        cpu: "2"
    livenessProbe:
      httpGet:
        path: /
        port: 8080
      initialDelaySeconds: 30
      periodSeconds: 10
      failureThreshold: 5
    readinessProbe:
      httpGet:
        path: /
        port: 8080
      initialDelaySeconds: 15
      periodSeconds: 5
      failureThreshold: 3
    volumeMounts:
    - name: zap-data
      mountPath: /zap/reports
      subPath: reports

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
