def call() {
    return """
apiVersion: apps/v1
kind: Deployment
metadata:
  name: zap
  namespace: devops-tools
spec:
  replicas: 1
  selector:
    matchLabels:
      app: zap
  template:
    metadata:
      labels:
        app: zap
    spec:
      containers:
        - name: zap
          image: zaproxy/zap-stable
          ports:
            - containerPort: 8080  # Default port ZAP runs on
          env:
            - name: ZAP_API_KEY
              value: "-5IrWmmH4xuGCAYjRI1CwOSoxNAHK1ErXfZ4KJ9vMv4"  # Set an API key if required
          command:
            - zap.sh
          args:
            - "-daemon"  # Run ZAP in daemon mode (headless)
            - "-host"
            - "0.0.0.0"  # Allow access from any IP
            - "-port"
            - "8080"  # Port ZAP listens on
            - "-config"
            - "connection.requestTimeoutInMs=60000"  # Optional: Set request timeout to 60 seconds
---
apiVersion: v1
kind: Service
metadata:
  name: zap-service
  namespace: devops-tools
spec:
  type: NodePort
  selector:
    app: zap
  ports:
    - protocol: TCP
      port: 9090  # The port to expose ZAP API
      targetPort: 8080  # Container port
      nodePort: 31003  # Port on the host machine (accessible externally)

"""
}
