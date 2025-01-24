// @Library('Shared-Libraries') _
// Fuction call --> docker(image:version)

def call(String image = 'docker:latest') {
    return """
apiVersion: v1
kind: Pod
spec:
  volumes:
  - name: docker-socket
    emptyDir: {}
  containers:
  - name: docker
    image: ${image}
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
"""
}