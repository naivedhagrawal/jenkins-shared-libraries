def call(String name = 'jnlp', String image = 'jenkins/inbound-agent:latest') {
    return """
apiVersion: v1
kind: Pod
spec:
  dnsConfig:
    nameservers:
      - 8.8.8.8
      - 1.1.1.1
  volumes:
    - name: zap-reports
      hostPath:
        path: /home/data/zap-reports
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
    """
}
