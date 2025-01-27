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
              withCredentials([string(credentialsId: 'SNYK_TOKEN', variable: 'SNYK_TOKEN')]) {
                echo "SNYK_TOKEN: \$SNYK_TOKEN"  # Debugging line
                echo "Running snyk tests"  # Debugging line
                snyk auth \$SNYK_TOKEN && \\
                ${scanCommands.replaceAll("\n", " \\\n")}
              }
            tty: true
    """
}
