def call(List stagesConfig) {
    // Iterate over each stage configuration provided by the user
    stagesConfig.each { stageConfig ->
        
        // Check if essential parameters are missing (name, podImage, podImageVersion, gitUrl)
        if (!stageConfig.name || !stageConfig.podImage || !stageConfig.podImageVersion || !stageConfig.gitUrl) {
            error "Missing required parameters: name, podImage, podImageVersion, gitUrl in stage configuration"
        }

        // Extract the container name from the pod image (e.g., 'my-build-image:v1.0.0' => 'my-build-image')
        def containerName = stageConfig.podImage.tokenize('/').last().tokenize(':').first()

        // Set the branch for the git checkout step, default to 'main' if no branch is specified
        def branch = stageConfig.branch ?: 'main'

        // Select the pod template file based on the stage's podTemplate configuration (use default template if not specified)
        def podTemplateName = stageConfig.podTemplate ?: 'podTemplate.yaml'  // Default to 'podTemplate.yaml'
        def podTemplate = libraryResource("kubernetes/${podTemplateName}") // Load the pod template file from resources

        // Replace placeholders in the pod template with the specific image and container name
        podTemplate = podTemplate.replace('{{POD_IMAGE}}', stageConfig.podImage)
                                 .replace('{{POD_IMAGE_VERSION}}', stageConfig.podImageVersion)
                                 .replace('{{CONTAINER_NAME}}', containerName)

        // Create a pod based on the specified template
        podTemplate(podTemplate) {
            node(POD_LABEL) {
                // Define the build steps for each stage
                stage(stageConfig.name) {
                    
                    // Checkout the specified branch from the git repository
                    checkout scm: [
                        $class: 'GitSCM',
                        branches: [[name: "*/${branch}"]], // Checkout the branch dynamically
                        userRemoteConfigs: [[url: stageConfig.gitUrl]]  // Clone the repository using the git URL provided
                    ]

                    // If the stage has custom shell steps, execute them
                    if (stageConfig.steps) {
                        stageConfig.steps.each { step ->
                            sh step  // Execute each shell command defined in the stageConfig
                        }
                    } else {
                        // If no steps are defined for the stage, log a message
                        echo "No steps defined for stage: ${stageConfig.name}"
                    }
                }
            }
        }
    }
}
