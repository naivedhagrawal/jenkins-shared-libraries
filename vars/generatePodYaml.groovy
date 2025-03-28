// Function to generate the pod YAML
def generatePodYaml(List<Map> containers, String GIT_URL, String GIT_BRANCH) {
    def containerYaml = containers.collect { container ->
        """
    - name: ${container.name}
      image: ${container.image}
      tty: true
      volumeMounts:
        - name: source-code
          mountPath: /source
        """
    }.join('\n')

    return """
apiVersion: v1
kind: Pod
spec:
  containers:
${containerYaml}
  initContainers:
    - name: git-clone
      image: alpine/git
      command: ['sh', '-c']
      args: ["git clone '${GIT_URL}' -b '${GIT_BRANCH}' /source"]
      volumeMounts:
        - name: source-code
          mountPath: /source
  volumes:
    - name: source-code
      emptyDir: {}
"""
}
