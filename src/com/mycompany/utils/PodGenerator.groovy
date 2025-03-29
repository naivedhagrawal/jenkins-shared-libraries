package com.mycompany.utils

class PodGenerator implements Serializable {
    static String generatePodYaml(List<Map> containers) {
        def containerYaml = containers.collect { container ->
            def volumeMounts = (container.volumeMounts ?: []) as List<Map>

            def volumeMountsYaml = volumeMounts.collect { mount ->
                """
            - name: ${mount.name}
              mountPath: ${mount.mountPath}
                """.stripIndent()
            }.join('\n')

            """
        - name: ${container.name}
          image: ${container.image}
          tty: true
          command:
            - /bin/sh
            - -c
            - sleep infinity
          volumeMounts:
        ${volumeMounts ? volumeMountsYaml : '[]'} 
            """.stripIndent()
        }.join('\n')

        // Collect unique volumes
        def volumes = containers.findAll { it.volumeMounts instanceof List }
            .collectMany { it.volumeMounts ?: [] }
            .unique { it.name }
            .collect { vol ->
                """
        - name: ${vol.name}
          emptyDir: {}
                """.stripIndent()
            }.join('\n')

        // Add workspace volume to avoid NullPointerException
        def workspaceVolume = """
      volumes:
        - name: jenkins-workspace-volume
          emptyDir: {}
        ${volumes}
        """.stripIndent()

        return """
    apiVersion: v1
    kind: Pod
    spec:
      containers:
    ${containerYaml}
      ${workspaceVolume}
    """.stripIndent()
    }
}
