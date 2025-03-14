def call() {
  return """
apiVersion: v1
kind: Pod
metadata:
  name: zap-scanner
  namespace: devops-tools
  labels:
    app: zap
spec:
  initContainers:
    - name: init-permissions
      image: busybox
      command: ["sh", "-c", "mkdir -p /home/zap && chown -R 1000:1000 /home/zap && chmod -R 775 /home/zap"]
      volumeMounts:
        - name: zap-home
          mountPath: /home/zap
  containers:
    - name: zap
      image: zaproxy/zap-stable
      workingDir: /home/zap
      args: ["zap.sh", "-daemon", "-host", "0.0.0.0", "-port", "8090", "-config", "api.disablekey=true", "-config", "api.addrs.addr.name=.*", "-config", "api.addrs.addr.regex=true", "-addonupdate", "-addoninstall", "reports", "-addoninstall", "reportTemplates", "-Xmx8192m"]
      ports:
        - containerPort: 8090
      securityContext:
        runAsUser: 1000
        runAsGroup: 1000
      livenessProbe:
        httpGet:
          path: /JSON/core/view/version/
          port: 8090
        initialDelaySeconds: 10
        periodSeconds: 30
      readinessProbe:
        httpGet:
          path: /JSON/core/view/version/
          port: 8090
        initialDelaySeconds: 5
        periodSeconds: 10
      lifecycle:
        postStart:
          exec:
            command: ["/bin/sh", "-c", "chown -R 1000:1000 /home/zap && chmod -R 775 /home/zap"]
      volumeMounts:
        - name: zap-home
          mountPath: /home/zap
    - name: curl-jq
      image: curlimages/curl
      command: ["sh", "-c", "while true; do curl -s zap:8090/JSON/core/view/version/ | jq .; sleep 30; done"]
  volumes:
    - name: zap-home
      emptyDir: {}
"""
}
