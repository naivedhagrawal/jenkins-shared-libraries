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
      - "export ZAP_CLI_API_KEY=\$(cat /proc/sys/kernel/random/uuid) && \
         echo \$ZAP_CLI_API_KEY > /zap/wrk/api-key.txt && \
         export JVM_ARGS='-Xmx6g' && zap.sh -daemon -host 0.0.0.0 -port 8080 \
         -config api.addrs.addr.name=.* -config api.addrs.addr.regex=true \
         -config api.disablekey=false -config api.key=\$ZAP_CLI_API_KEY && \
         echo 'Waiting for ZAP to start...' && \
         for i in {1..30}; do curl -s http://localhost:8080 && break || sleep 5; done && \
         tail -f /zap/.ZAP_D/logs/zap.log"
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
    securityContext:
      runAsUser: 1000
      runAsGroup: 1000
      fsGroup: 1000
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
      mountPath: /zap/wrk
      subPath: reports

  volumes:
  - name: zap-data
    emptyDir: {}

  restartPolicy: Always
"""
}
