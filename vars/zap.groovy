def call() {
    return '''
apiVersion: v1
kind: Pod
metadata:
  name: zap-pod
spec:
  containers:
    - name: zap-container
      image: zaproxy/zap-stable
      command: ["zap-webui"]
      ports:
        - containerPort: 8080
        - containerPort: 8081
      volumeMounts:
        - name: zap-home
          mountPath: /home/zap/.ZAP
  volumes:
    - name: zap-home
      emptyDir: {}
'''
}
