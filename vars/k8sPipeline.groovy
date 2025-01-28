def call(List stagesConfig) {
    stagesConfig.each { stageConfig ->
        if (!stageConfig.name || !stageConfig.podImage || !stageConfig.podImageVersion || !stageConfig.gitUrl) {
            error "Missing required parameters: name, podImage, podImageVersion, gitUrl in stage configuration"
        }

        // Extract container name from pod image (e.g., 'my-build-image' from 'my-build-image:v1.0.0')
        def containerName = stageConfig.podImage.tokenize('/').last().tokenize(':').first()

        // Set the branch (default to 'main' if not specified)
        def branch = stageConfig.branch ?: 'main'

        def podTemplate = libraryResource('kubernetes/podTemplate.yaml')
        podTemplate = podTemplate.replace('{{POD_IMAGE}}', stageConfig.podImage)
                                 .replace('{{POD_IMAGE_VERSION}}', stageConfig.podImageVersion)
                                 .replace('{{CONTAINER_NAME}}', containerName)

        podTemplate(podTemplate) {
            node(POD_LABEL) {
                stage(stageConfig.name) {
                    // Checkout code for this stage
                    checkout scm: [
                        $class: 'GitSCM',
                        branches: [[name: "*/${branch}"]],
                        userRemoteConfigs: [[url: stageConfig.gitUrl]]
                    ]

                    // Execute stage-specific logic
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
