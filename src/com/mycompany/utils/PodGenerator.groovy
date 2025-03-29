package com.mycompany.utils

class PodGenerator implements Serializable {
    static String generatePodYaml(List<Map> containers) {
        def containerYaml = containers.collect { container ->
            def volumeMounts = (container.volumeMounts ?: []) as List<Map>

            // Use specified mounts or fallback to empty list
            def volumeMountsYaml = volumeMounts ? volumeMounts.collect { mount ->
                """
            - name: ${mount.name}
              mountPath: ${mount.mountPath}
                """.stripIndent()
            }.join('\n') : "[]"

            """
        - name: ${container.name}
          image: ${container.image}
          tty: true
          command:
            - /bin/sh
            - -c
            - sleep infinity
          volumeMounts: 
        ${volumeMountsYaml}
            """.stripIndent()
        }.join('\n')

        // Collect unique volumes, include default volume
        def volumes = containers.findAll { it.volumeMounts instanceof List }
            .collectMany { it.volumeMounts ?: [] }
            .unique { it.name }
            .collect { vol ->
                """
        - name: ${vol.name}
          emptyDir: {}
                """.stripIndent()
            }.join('\n')

        // Add workspace volume and default volume
        def workspaceVolume = """
      volumes:
        - name: jenkins-workspace-volume
          emptyDir: {}
        - name: default-volume
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
