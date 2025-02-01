def call() {
    return """
apiVersion: v1
kind: Pod
metadata:
  name: trivy-scanner
spec:
  containers:
  - name: trivy
    image: aquasec/trivy:latest
    command: ["cat"]
    tty: true
  - name: dind
    image: docker:dind
    securityContext:
      privileged: true
    env:
    - name: DOCKER_TLS_CERTDIR
      value: ""
    """
}