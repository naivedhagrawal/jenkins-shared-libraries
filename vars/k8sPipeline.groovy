def call(List stagesConfig, String gitUrl = '', String defaultBranch = 'main') {
    stagesConfig.each { stageConfig ->
        if (!stageConfig.name || !stageConfig.podImage || !stageConfig.podImageVersion) {
            error "Missing required parameters: name, podImage, podImageVersion in stage configuration"
        }

        def containerName = stageConfig.podImage.tokenize('/').last().tokenize(':').first()
        def branch = stageConfig.branch ?: defaultBranch
        def podTemplateName = stageConfig.podTemplate ?: 'podTemplate.yaml'

        // Load and configure the pod template
        def podTemplate = libraryResource("kubernetes/${podTemplateName}")
        podTemplate = podTemplate.replace('{{POD_IMAGE}}', stageConfig.podImage)
                                 .replace('{{POD_IMAGE_VERSION}}', stageConfig.podImageVersion)
                                 .replace('{{CONTAINER_NAME}}', containerName)

        podTemplate(podTemplate) {
            node(POD_LABEL) {
                stage(stageConfig.name) {
                    // Checkout the code
                    checkout scm: [
                        $class: 'GitSCM',
                        branches: [[name: "*/${branch}"]],
                        userRemoteConfigs: [[url: gitUrl]]
                    ]

                    // Run stage-specific steps
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
