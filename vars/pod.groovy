def call(String name = 'jnlp', String image = 'jenkins/inbound-agent:latest') {
    return """
apiVersion: v1
kind: Pod
spec:
  containers:
    - name: ${name}
      image: "${image}"
      imagePullPolicy: Always
      command:
        - sleep
        - infinity
      tty: true
      resources:
        limits:
          cpu: "1"
          memory: "1Gi"
        requests:
          cpu: "0.5"
        """
    }
