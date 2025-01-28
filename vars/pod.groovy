def call(String image = 'jenkins/inbound-agent:latest', boolean showRawYaml = false) {
    return """
apiVersion: v1
kind: Pod
spec:
  containers:
    - name: jnlp
      image: "${image}"
      imagePullPolicy: Always
      command:
        - cat
      tty: true
    """
}