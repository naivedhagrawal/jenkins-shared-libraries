def call(String name = 'jnlp', String image = 'jenkins/inbound-agent:latest') {
    return """
apiVersion: v1
kind: Pod
metadata:
  labels:
    component: zap
spec:
  containers:
  - name: zap
    image: naivedh/owasp-zap:latest
    args: ["zap.sh", "-daemon", "-host", "0.0.0.0", "-port", "8080"]
    ports:
    - containerPort: 8080
    """
}