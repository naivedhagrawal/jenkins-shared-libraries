def call() {
    return """
apiVersion: v1
kind: Pod
metadata:
  labels:
    component: zap
spec:
  volumes:
    - name: zap-data
      emptyDir: {}
  containers:
  - name: zap
    image: zaproxy/zap-stable
    args: ["zap.sh", "-daemon", "-host", "0.0.0.0", "-port", "8080"]
    ports:
    - containerPort: 8080
    volumeMounts:
    - name: zap-data
      mountPath: /zap/.ZAP_HOME
    """
}