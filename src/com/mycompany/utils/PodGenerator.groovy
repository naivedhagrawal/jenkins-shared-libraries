package com.mycompany.utils

class PodGenerator implements Serializable {
    static String generatePodYaml(List<Map> containers) {
        def containerYaml = containers.collect { container ->
            def volumeMountsYaml = container.volumeMounts?.collect { mount ->
                """
            - name: ${mount.name}
              mountPath: ${mount.mountPath}
                """.stripIndent()
            }?.join('\n') ?: ''

            """
        - name: ${container.name}
          image: ${container.image}
          tty: true
          command:
            - /bin/sh
            - -c
            - sleep infinity
          ${volumeMountsYaml ? 'volumeMounts:' : ''}
        ${volumeMountsYaml}
            """.stripIndent()
        }.join('\n')

        // Collect all unique volumes from only the containers that have volume mounts
        def volumes = containers.findAll { it.volumeMounts }
            .collectMany { it.volumeMounts }
            .unique { it.name }
            .collect { vol ->
                """
        - name: ${vol.name}
          emptyDir: {}
                """.stripIndent()
            }.join('\n')

        return """
    apiVersion: v1
    kind: Pod
    spec:
      containers:
    ${containerYaml}
      ${volumes ? 'volumes:' : ''}
    ${volumes}
    """.stripIndent()
    }
}
