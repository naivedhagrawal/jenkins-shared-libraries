package com.mycompany.utils

import java.io.Serializable

class PodGenerator implements Serializable {
    /**
     * Generates a Kubernetes Pod YAML configuration string.
     *
     * @param containers A list of maps, where each map represents a container.
     * Each container map should contain the following keys:
     * - name (String): The name of the container.
     * - image (String): The Docker image to use for the container.
     * - volumeMounts (List<Map>, optional): A list of volume mount maps.
     * Each volume mount map should contain:
     * - name (String): The name of the volume.
     * - mountPath (String): The path where the volume should be mounted.
     *
     * @return A YAML string representing the Pod configuration.
     * The YAML includes:
     * - apiVersion: v1
     * - kind: Pod
     * - spec:
     * - containers: (defined by the input)
     * - volumes: (derived from container volumeMounts, plus default volumes)
     * The function adds a default volume called "jenkins-workspace-volume".
     * If a container doesn't have volumeMounts, it defaults to an empty list.
     * If volumeMounts are specified, it generates the appropriate YAML.
     */
    static String generatePodYaml(List<Map> containers) {
        // Process each container definition
        def containerYaml = containers.collect { container ->
            // Safely handle null or missing volumeMounts.  Ensure it's always a List.
            def volumeMounts = (container.volumeMounts ?: []) as List<Map>

            // Generate YAML for volumeMounts, handle empty list case
            def volumeMountsYaml = volumeMounts ? volumeMounts.collect { mount ->
                """
            - name: ${mount.name}
              mountPath: ${mount.mountPath}
                """.stripIndent()
            }.join('\n') : "[]"

            // Construct the container YAML
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

        // Collect unique volumes from all containers
        def volumes = containers.findAll { it.volumeMounts instanceof List }
                .collectMany { it.volumeMounts ?: [] } //handle null volumeMounts
                .unique { it.name } // Ensure volume names are unique
                .collect { vol ->
                    """
        - name: ${vol.name}
          emptyDir: {}
                """.stripIndent()
                }.join('\n')

        // Construct the volumes YAML, including the default volume
        def workspaceVolume = """
      volumes:
        - name: jenkins-workspace-volume
          emptyDir: {}
        - name: default-volume
          emptyDir: {}
        ${volumes}
        """.stripIndent()

        // Construct the complete Pod YAML
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
