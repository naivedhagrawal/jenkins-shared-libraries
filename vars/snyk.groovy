// @Library('Shared-Libraries') _
// Fuction call --> pod()
// naivedh/snyk-image:latest

def snyk() {
    return """
apiVersion: v1
kind: Pod
metadata:
  name: snyk-pod
spec:
  containers:
  - name: snyk
    image: naivedh/snyk-image:latest # Your Snyk image
    imagePullPolicy: IfPresent # or Always if you want to always pull the latest
    volumeMounts:
      - name: workspace-volume
        mountPath: /app
    env:
      - name: SNYK_TOKEN
        valueFrom:
          secretKeyRef:
            name: snyk-secret # Name of your Kubernetes Secret
            key: snyk-token
  volumes:
  - name: workspace-volume
    persistentVolumeClaim:
      claimName: workspace-pvc # Name of your PersistentVolumeClaim
"""
}