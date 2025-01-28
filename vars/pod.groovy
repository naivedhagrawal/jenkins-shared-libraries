def call(String name = 'jnlp', String image = 'jenkins/inbound-agent:latest', boolean showRawYaml = false) {
    def yaml = """
apiVersion: v1
kind: Pod
spec:
  containers:
    - name: ${name}
      image: "${image}"
      imagePullPolicy: Always
      command:
        - cat
      tty: true
    """
    
    // Only show YAML if showRawYaml is true
    if (showRawYaml) {
        echo "Raw YAML:"
        echo yaml
    }
    
    return yaml
}
