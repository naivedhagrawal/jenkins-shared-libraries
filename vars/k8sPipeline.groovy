@Grab('org.yaml:snakeyaml:2.0')
import io.fabric8.kubernetes.api.model.Pod
import org.yaml.snakeyaml.Yaml
import com.fasterxml.jackson.databind.ObjectMapper

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

        // CORRECT WAY TO USE THE POD OBJECT
        kubernetes.pod(pod).start() // Start the pod

        try {
            node(POD_LABEL) {  // Use the label to connect to the started pod
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
        } finally {
            kubernetes.pod(pod).delete() // Ensure pod deletion even if errors occur
        }
    }
}


def loadPodTemplate(String podTemplateName, Map stageConfig, String containerName) {
    def yaml = new Yaml()
    def podYaml = libraryResource("kubernetes/${podTemplateName}")
    def pod = yaml.load(podYaml) as LinkedHashMap

    def mapper = new ObjectMapper()
    pod = mapper.convertValue(pod, Pod.class)

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