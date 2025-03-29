/* def containers = [
        [name: 'git', image: 'alpine/git:latest'],
        [name: 'gitleak', image: 'zricethezav/gitleaks:latest'],
        [name: 'owasp', image: 'owasp/dependency-check-action:latest'],
        [name: 'semgrep', image: 'returntocorp/semgrep:latest'],
        [name: 'checkov', image: 'bridgecrew/checkov:latest']
    ]

    def podYaml = PodGenerator.generatePodYaml(containers) */



package com.mycompany.utils

class PodGenerator implements Serializable {
    static String generatePodYaml(List<Map> containers) {
        def containerYaml = containers.collect { container ->
            """
        - name: ${container.name}
          image: ${container.image}
          tty: true
          command:
            - /bin/sh
            - -c
            - sleep infinity
          volumeMounts:
            - name: default
              mountPath: /default
            """
        }.join('\n')

        return """
    apiVersion: v1
    kind: Pod
    spec:
      containers:
    ${containerYaml}
      volumes:
        - name: default
          emptyDir: {}
    """
    }
}
