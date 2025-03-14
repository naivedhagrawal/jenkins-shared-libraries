def call() {
  return """
apiVersion: v1
kind: Pod
metadata:
  name: zap
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
      args: ["zap.sh", "-daemon", "-host", "0.0.0.0", "-port", "8090", "-config", "api.disablekey=true", "-config", "api.addrs.addr.name=.*", "-config", "api.addrs.addr.regex=true", "-addonupdate", "-addoninstall", "reports", "-addoninstall", "reportTemplates", "-Xmx8192m", "-config", "scanner.threadPerHost=5", "-config", "selenium.headless=false", "-config", "spider.threads=2", "-config", "ascan.threads=2"]
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
            command: ["/bin/sh", "-c", "chown -R 1000:1000 /home/zap && chmod -R 775 /home/zap && until curl -s http://localhost:8090/JSON/core/view/version/; do sleep 5; done && curl -s http://localhost:8090/JSON/autoupdate/action/installAddon/?id=reports && curl -s http://localhost:8090/JSON/autoupdate/action/installAddon/?id=reportTemplates"]
      volumeMounts:
        - name: zap-home
          mountPath: /home/zap
    - name: curl-jq
      image: badouralix/curl-jq
      command:
        - "/bin/sh"
        - "-c"
        - |
          echo "Waiting for ZAP to start...";
          until curl -s http://localhost:8090/JSON/core/view/version/; do
            sleep 5;
          done;
          echo "ZAP is ready! Running security tests...";
          curl -s "http://localhost:8090/JSON/ascan/action/scan/?url=http://target.com&recurse=true"
  volumes:
    - name: zap-home
      emptyDir: {}
"""
}
