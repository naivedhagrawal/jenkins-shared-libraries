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
        command: ["sleep"]
        args: ["999999"]
        resources:
          limits:
            cpu: "1"
            memory: "2Gi"
          requests:
            cpu: "500m"
          volumeMounts:
        - name: docker-socket
          mountPath: /var/run
        - name: trivy-cache
          mountPath: /root/.cache/trivy
      - name: docker
        image: docker:latest
        command: ["sleep"]
        args: ["99d"]
        readinessProbe:
          exec:
            command: [sh, -c, "ls -S /var/run/docker.sock"]
          initialDelaySeconds: 5
          periodSeconds: 5
        resources:
          limits:
            cpu: "1"
            memory: "2Gi"
          requests:
            cpu: "500m"
        volumeMounts:
        - name: docker-socket
          mountPath: /var/run
      - name: docker-daemon
        image: docker:dind
        securityContext:
          privileged: true
        command: ["dockerd"]
        resources:
          limits:
            cpu: "1"
            memory: "2Gi"
          requests:
            cpu: "500m"
        volumeMounts:
        - name: docker-socket
          mountPath: /var/run
      volumes:
      - name: docker-socket
        emptyDir: {}
      - name: trivy-cache
        emptyDir: {}
        """
}
