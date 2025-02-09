def call(String name = 'jnlp', String image = 'jenkins/inbound-agent:latest') {
    return """
apiVersion: v1
kind: Pod
spec:
  containers:
    - name: ${name}
      image: "${image}"
      imagePullPolicy: Always
      resources:
        limits:
          memory: "512Mi"
          cpu: "500m"
      command:
        - cat
      tty: true
    """