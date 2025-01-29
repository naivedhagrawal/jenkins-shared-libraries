def call() {
    return """
apiVersion: apps/v1
kind: Deployment
metadata:
  name: zap
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
          image: zaproxy/zap-stable:latest
          args: ["zap.sh", "-daemon", "-port", "8080", "-host", "0.0.0.0"]
          ports:
            - containerPort: 8080

    """
}