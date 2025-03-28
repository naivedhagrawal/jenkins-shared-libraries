package com.mycompany.utils

class PodGenerator implements Serializable {
    static String generatePodYaml(List<Map> containers, String GIT_URL, String GIT_BRANCH) {
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
          tty: true
          command: ['sh', '-c']
          args: ["git clone -b ${GIT_BRANCH} ${GIT_URL} /source"]
          command:
            - sleep
            - infinity
          volumeMounts:
            - name: source-code
              mountPath: /source
      volumes:
        - name: source-code
          emptyDir: {}
    """
    }
}
