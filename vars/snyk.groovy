/**
 * Executes Snyk security scans based on the project type and scan options.
 *
 * @param projectType The type of the project (e.g., 'maven', 'node', 'python').
 * @param runImageScan Whether to run a Docker image scan.
 * @param imageName The name of the Docker image to scan.
 * @param runIacScan Whether to run an Infrastructure as Code (IaC) scan.
 */
def call(String projectType, boolean runImageScan = false, String imageName = '', boolean runIacScan = false) {
    def snykImage = ''
    switch (projectType) {
        case 'maven':
            snykImage = 'snyk/snyk:maven'
            break
        case 'node':
            snykImage = 'snyk/snyk:node'
            break
        case 'python':
            snykImage = 'snyk/snyk:python'
            break
        default:
            error "Unsupported project type: ${projectType}"
    }

    // Validate IaC scan configuration
    if (runIacScan) {
        if (!env.IAC_CONFIG) {
            error "IaC scan is enabled, but IAC_CONFIG is not set."
        }
    }

    // Prepare the scan commands
    if (runImageScan && imageName.trim().isEmpty()) {
        error "Image name must be provided when runImageScan is true."
    }

    // Prepare the scan commands
    def scanCommands = "snyk test"
    if (runImageScan) {
        scanCommands += "\nsnyk test --docker ${imageName}"
    }
    if (runIacScan) {
        scanCommands += "\nsnyk iac test"
    }

    // Return the Kubernetes YAML for the pod
    return """
    apiVersion: v1
    kind: Pod
    spec:
      containers:
      - name: snyk
        image: ${snykImage}
        imagePullPolicy: Always
        command:
        - sh
        - -c
        - |
          snyk auth \$SNYK_TOKEN && \\
          ${scanCommands.replaceAll("\n", " && \\\n")}
        tty: true
    """
}
