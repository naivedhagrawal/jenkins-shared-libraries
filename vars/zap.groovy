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
    command: ["/bin/sh", "-c"]
    args:
      - "export ZAP_CLI_API_KEY=\$(uuidgen) && echo \$ZAP_CLI_API_KEY > /zap-api-key.txt && \
         export JVM_ARGS='-Xmx6g' && zap.sh -daemon -host 0.0.0.0 -port 8080 \
         -config api.addrs.addr.name=.* -config api.addrs.addr.regex=true \
         -config api.disablekey=false -config api.key=\$ZAP_CLI_API_KEY && \
         sleep 10 && tail -f /dev/null"
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
      failureThreshold: 10
    readinessProbe:
      httpGet:
        path: /
        port: 8080
      initialDelaySeconds: 15
      periodSeconds: 5
      failureThreshold: 5
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
