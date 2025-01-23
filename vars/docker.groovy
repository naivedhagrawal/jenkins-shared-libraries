def call(String version = 'latest') {
    return """
    apiVersion: v1
    kind: Pod
    spec:
      containers:
      - name: jnlp
        image: jenkins/inbound-agent:latest
        imagePullPolicy: Always
        securityContext:
          privileged: true
      - name: docker
        image: docker:${version}
        readinessProbe:
          exec:
            command: ['sh', '-c', 'ls -S /var/run/docker.sock']
          initialDelaySeconds: 5
          periodSeconds: 5
        command: ['sleep']
        args: ['99d']
        volumeMounts:
        - name: docker-socket
          mountPath: /var/run
      - name: docker-daemon
        image: docker:dind
        securityContext:
          privileged: true
        command: ['dockerd']
        volumeMounts:
        - name: docker-socket
          mountPath: /var/run
      volumes:
      - name: docker-socket
        emptyDir: {}
    """
}
