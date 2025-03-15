def call(String name = 'jnlp', String image = 'jenkins/inbound-agent:latest') {
    return """
apiVersion: v1
kind: Pod
spec:
  volumes:
    - name: zap-reports
      hostPath:
        path: /home/data/zap-reports
        type: DirectoryOrCreate
    - name: nvd-data
      hostPath:
        path: /home/data/nvd-data
        type: DirectoryOrCreate
  containers:
    - name: ${name}
      image: "${image}"
      imagePullPolicy: Always
      command:
        - sleep
        - infinity
      tty: true
      volumeMounts:
        - name: zap-reports
          mountPath: /zap/reports
        - name: nvd-data
          mountPath: /usr/share/dependency-check/data
    """
}
