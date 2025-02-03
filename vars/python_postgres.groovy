def call() {
    return """
apiVersion: v1
kind: Pod
spec:
  containers:
    - name: python
      image: python:latest
      imagePullPolicy: Always
      command:
        - cat
      tty: true
    - name: postgres
      image: postgres:15
      imagePullPolicy: Always
      command:
        - cat
      tty: true
    """
}