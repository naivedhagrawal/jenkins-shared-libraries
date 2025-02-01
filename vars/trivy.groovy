def call() {
    return """
apiVersion: v1
kind: Pod
metadata:
  name: trivy-scanner
spec:
  volumes:
  - name: docker-socket
    emptyDir: {}
  - name: workspace
    emptyDir: {}
  containers:
  - name: docker-daemon
    image: docker:dind
    securityContext:
      privileged: true
    command: ["dockerd", "--host", "tcp://0.0.0.0:2375", "--host", "unix:///var/run/docker.sock"]
    volumeMounts:
    - name: docker-socket
      mountPath: /var/run
  - name: docker
    image: docker:latest
    env:
    - name: DOCKER_HOST
      value: "tcp://localhost:2375"
    command:
    - sleep
    args:
    - "99d"
    volumeMounts:
    - name: docker-socket
      mountPath: /var/run
  - name: trivy
    image: aquasec/trivy:latest
    command: ["cat"]
    volumeMounts:
    - name: docker-socket
      mountPath: /var/run
    - name: workspace
      mountPath: /workspace
"""
}