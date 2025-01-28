import io.fabric8.kubernetes.api.model.Pod

def call(List stagesConfig, String gitUrl = '', String defaultBranch = 'main') {
    def POD_LABEL = "jenkins-agent-${UUID.randomUUID().toString()}" // Define POD_LABEL

    stagesConfig.each { stageConfig ->
        if (!stageConfig.name || !stageConfig.podImage || !stageConfig.podImageVersion) {
            error "Missing required parameters: name, podImage, podImageVersion in stage configuration"
        }

        def containerName = stageConfig.podImage.tokenize('/').last().tokenize(':').first()
        def branch = stageConfig.branch ?: defaultBranch
        def podTemplateName = stageConfig.podTemplate ?: 'podTemplate.yaml'

        // Load and configure the pod template (correctly)
        def pod = loadPodTemplate(podTemplateName, stageConfig, containerName)

        pod {
            label(POD_LABEL) // Set the label on the pod
            node(POD_LABEL) {
                stage(stageConfig.name) {
                    checkout scm: [
                        $class: 'GitSCM',
                        branches: [[name: "*/${branch}"]],
                        userRemoteConfigs: [[url: gitUrl]]
                    ]

                    if (stageConfig.steps) {
                        stageConfig.steps.each { step ->
                            sh step
                        }
                    } else {
                        echo "No steps defined for stage: ${stageConfig.name}"
                    }
                }
            }
        }
    }
}


def loadPodTemplate(String podTemplateName, Map stageConfig, String containerName) {
    def pod = readYaml(libraryResource("kubernetes/${podTemplateName}")) as Pod

    // Set the image and version
    pod.spec.containers.each { container ->
        if (container.name == containerName || container.name == "{{CONTAINER_NAME}}") { // Check both existing name and placeholder
            container.image = "${stageConfig.podImage}:${stageConfig.podImageVersion}"
        }
    }
        // Set the name if it exists
        pod.spec.containers.each { container ->
        if (container.name == "{{CONTAINER_NAME}}") { // Check both existing name and placeholder
            container.name = containerName
        }
    }

    return pod
}