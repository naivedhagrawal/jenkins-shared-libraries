def call() {
    return '''apiVersion: v1
kind: Pod
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
        - "zap.sh"
        - "-daemon"  # Run ZAP in daemon mode (headless)
      args:
        - "-host"
        - "0.0.0.0"  # Allow access from any IP
        - "-port"
        - "8080"  # Port ZAP listens on
        - "-config"
        - "connection.requestTimeoutInMs=60000"  # Optional: Set request timeout to 60 seconds'''
}
