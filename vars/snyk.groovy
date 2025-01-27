// @Library('Shared-Libraries') _
// Fuction call --> pod(image:version)

def call(String image = 'none', boolean showRawYaml = false) {
    return """
        apiVersion: v1
        kind: Pod
        spec:
          containers:
          - name: snyk
            image: ${image}
            imagePullPolicy: Always
            command:
            - cat
            tty: true
        """
}