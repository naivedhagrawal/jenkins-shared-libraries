def generatePodYaml(List<Map> containers) {
    def containerYaml = containers.collect { container ->
        """
    - name: ${container.name}
      image: ${container.image}
      command:
        - cat
      tty: true
      volumeMounts:
        - name: workspace-volume
          mountPath: /workspace
        """
    }.join('\n')

    return """
apiVersion: v1
kind: Pod
spec:
  containers:
${containerYaml}
  volumes:
    - name: workspace-volume
      persistentVolumeClaim:
        claimName: jenkins-workspace-pvc
"""
}
