@Grab('org.yaml:snakeyaml:2.0') // Add SnakeYAML dependency

import io.fabric8.kubernetes.api.model.Pod
import org.yaml.snakeyaml.Yaml

def call(List stagesConfig, String gitUrl = '', String defaultBranch = 'main') {
    def POD_LABEL = "jenkins-agent-${UUID.randomUUID().toString()}"

    stagesConfig.each { stageConfig ->
        if (!stageConfig.name || !stageConfig.podImage || !stageConfig.podImageVersion) {
            error "Missing required parameters: name, podImage, podImageVersion in stage configuration"
        }

        def containerName = stageConfig.podImage.tokenize('/').last().tokenize(':').first()
        def branch = stageConfig.branch ?: defaultBranch
        def podTemplateName = stageConfig.podTemplate ?: 'podTemplate.yaml'

        def pod = loadPodTemplate(podTemplateName, stageConfig, containerName)

        pod {
            label(POD_LABEL)
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
    def yaml = new Yaml()
    def podYaml = libraryResource("kubernetes/${podTemplateName}")
    def pod = yaml.load(podYaml) as LinkedHashMap // Load as LinkedHashMap first

    // Convert to Pod object (more robust)
    def mapper = new com.fasterxml.jackson.databind.ObjectMapper()
    pod = mapper.convertValue(pod, Pod.class)

    // Set image and version
    pod.spec.containers.each { container ->
        if (container.name == containerName || container.name == "{{CONTAINER_NAME}}") {
            container.image = "${stageConfig.podImage}:${stageConfig.podImageVersion}"
        }
    }

    pod.spec.containers.each { container ->
        if (container.name == "{{CONTAINER_NAME}}") {
            container.name = containerName
        }
    }
    return pod
}