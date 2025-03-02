def call(String name = 'docker' , String image = 'docker:latest') {
    return """
apiVersion: v1
kind: Pod
spec:
  volumes:
  - name: docker-socket
    emptyDir: {}
  containers:
  - name: ${name}
    image: ${image}
    resources:
      limits:
        cpu: "1"
        memory: "1Gi"
      requests:
    readinessProbe:
      exec:
        command: [sh, -c, "ls -l /var/run/docker.sock"]
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
    resources:
      limits:
        cpu: "1"
        memory: "1Gi"
      requests:
    command: ["dockerd"]
    volumeMounts:
    - name: docker-socket
      mountPath: /var/run
"""
}
