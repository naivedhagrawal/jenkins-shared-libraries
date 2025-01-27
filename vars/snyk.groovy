// @Library('Shared-Libraries') _
// Fuction call --> pod()

def call() {
    return """
        apiVersion: v1
        kind: Pod
        spec:
          containers:
          - name: snyk
            image: naivedh/snyk-image:latest
            imagePullPolicy: Always
            command:
            - cat
            tty: true
        """
}