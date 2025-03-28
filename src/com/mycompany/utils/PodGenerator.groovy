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
      volumes:
        - name: source-code
          emptyDir: {}
    """
    }
}
