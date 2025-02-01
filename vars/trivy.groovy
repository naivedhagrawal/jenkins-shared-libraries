def call() {
    return """
apiVersion: v1
kind: Pod
spec:
  volumes:
  - name: docker-socket
    emptyDir: {}
  containers:
  - name: docker
    image: docker:latest
    readinessProbe:
      exec:
        command: [sh, -c, "ls -S /var/run/docker.sock"]
      initialDelaySeconds: 5
      periodSeconds: 5
    command:
    - sleep
    args:
    - 99d
    volumeMounts:
    - name: docker-socket
      mountPath: /var/run
  - name: docker-daemon
    image: docker:dind
    securityContext:
      privileged: true
    command: ["dockerd"]
    volumeMounts:
    - name: docker-socket
      mountPath: /var/run
  - name: trivy
    image: aquasec/trivy:latest
    command: ["trivy"]
    args:
    - "--quiet"
    - "--no-progress"
    - "--format"
    - "json"
    - "--output"
    - "/tmp/trivy-report.json"
    - "/var/run/docker.sock"
    volumeMounts:
    - name: docker-socket
      mountPath: /var/run
"""
}