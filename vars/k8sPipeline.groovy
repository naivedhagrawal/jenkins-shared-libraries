// k8sPipeline.groovy
@GrabResolver(name='jenkins', root='https://repo.jenkins-ci.org/public/')
@Grab('org.jenkins-ci.plugins:kubernetes:4306.vc91e951ea_eb_d') // Replace with the correct version
@Grab('org.yaml:snakeyaml:2.0')
import io.fabric8.kubernetes.api.model.Pod
import org.yaml.snakeyaml.Yaml
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

        def kubernetes = Kubernetes.withDefaults()
        kubernetes.pods().inNamespace(kubernetes.getNamespace()).createOrReplace(pod) // Use createOrReplace

        try {
            // Use a more robust way to wait for the pod to be ready
            waitUntilPodIsReady(kubernetes, pod.getMetadata().getName())

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
            kubernetes.pods().inNamespace(kubernetes.getNamespace()).delete(pod) // Delete pod in the correct namespace.
        }
    }
}

// Helper function to load and configure the pod template
def loadPodTemplate(String podTemplateName, def stageConfig, String containerName) {
    def yaml = new Yaml()
    def podYaml = new File(podTemplateName).text
    def pod = yaml.load(podYaml)

    // Set the container image and name
    pod.spec.containers.each { container ->
        if (container.name == "{{CONTAINER_NAME}}") { // Correctly target the placeholder
            container.image = "${stageConfig.podImage}:${stageConfig.podImageVersion}"
            container.name = containerName
        }
    }
    return pod
}

// Helper function to wait for pod readiness
def waitUntilPodIsReady(kubernetes, podName) {
    timeout(60) { // Timeout after 60 seconds
        while (true) {
            def podStatus = kubernetes.pods().inNamespace(kubernetes.getNamespace()).withName(podName).get().getStatus()
            if (podStatus.getPhase() == "Running" && podStatus.getContainerStatuses().every { it.ready }) {
                echo "Pod ${podName} is ready."
                break
            }
            echo "Waiting for pod ${podName} to become ready..."
            sleep(5000) // Check every 5 seconds
        }
    }
}