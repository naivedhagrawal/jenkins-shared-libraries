@Grab('org.yaml:snakeyaml:2.0')
import io.fabric8.kubernetes.api.model.Pod
import org.yaml.snakeyaml.Yaml
import com.fasterxml.jackson.databind.ObjectMapper
import org.jenkinsci.plugins.kubernetes.Kubernetes

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

        // Get Kubernetes context
        def kubernetes = Kubernetes.withDefaults()  // Get the Kubernetes object

        kubernetes.pod(pod).start()

        try {
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
        } finally {
            kubernetes.pod(pod).delete()
        }
    }
}

// ... (rest of the code remains the same)