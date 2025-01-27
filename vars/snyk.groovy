// @Library('Shared-Libraries') _
// Fuction call --> pod(image:version)

def call(String projectType) {
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
    return """
        apiVersion: v1
        kind: Pod
        spec:
          containers:
          - name: snyk
            image: ${snykImage}
            imagePullPolicy: Always
            command:
            - cat
            tty: true
        """
}