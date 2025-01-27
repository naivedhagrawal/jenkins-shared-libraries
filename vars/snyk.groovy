// @Library('Shared-Libraries') _
// Fuction call --> pod()

def call() {
    return """
        apiVersion: v1
        kind: Pod
        spec:
          containers:
          - name: snyk
            image: snyk/snyk
            imagePullPolicy: Always
            command:
            - cat
            tty: true
        """
}