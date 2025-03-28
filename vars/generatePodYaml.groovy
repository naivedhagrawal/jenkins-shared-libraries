    // Function to generate the pod YAML
    def generatePodYaml(List<Map> containers) {
        // Define the git container
        def gitContainer = [
            name: 'git',
            image: 'alpine/git',
            command: [
                'git',
                'clone',
                "\${GIT_URL}",
                '-b',
                "\${GIT_BRANCH}",
                '/source'
            ],
            tty: true,
            volumeMounts: [
                [
                    name: 'source-code',
                    mountPath: '/source'
                ]
            ],
            env: [
                [
                    name: 'GIT_URL',
                    value: "\${GIT_URL}"
                ],
                [
                    name: 'GIT_BRANCH',
                    value: "\${GIT_BRANCH}"
                ]
            ]
        ]

        // Ensure 'git' is the first container in the list
        containers = [gitContainer] + containers.findAll { it.name != 'git' }

        def containerYaml = containers.collect { container ->
           
            
                """
    - name: ${container.name}
      image: ${container.image}
      command:
        - sleep
        - infinity
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
  initContainers:  // Use an init container for git checkout
    - name: git
      image: alpine/git
      command:
        - git
      args:
        - clone
        - "\${GIT_URL}"
        - "-b"
        - "\${GIT_BRANCH}"
        - /source"
      volumeMounts:
        - name: source-code
          mountPath: /source
      env:
        - name: GIT_URL
          value: "\${GIT_URL}"
        - name: GIT_BRANCH
          value: "\${GIT_BRANCH}"
  volumes:
    - name: source-code
      emptyDir: {}
"""
    }