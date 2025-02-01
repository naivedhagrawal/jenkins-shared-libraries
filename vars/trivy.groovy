def trivy() {
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
      - name: docker
        image: docker:latest
        command: ["sleep"]
        args: ["999999"]
    """
}