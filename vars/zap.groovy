def call() {
    return """
apiVersion: v1
kind: Pod
spec:
  containers:
    - name: zap
      image: zaproxy/zap-stable
      command: ["/bin/sh", "-c", "tail -f /dev/null"]
      securityContext:
        runAsUser: 1000
        runAsGroup: 1000
        readOnlyRootFilesystem: false
        fsGroup: 1000
      volumeMounts:
        - name: zap-wrk
          mountPath: /zap/wrk
          subPath: wrk
          readOnly: false
      tty: true

  volumes:
    - name: zap-wrk
      persistentVolumeClaim:
        claimName: zap-pvc
---
apiVersion: v1
kind: PersistentVolume
metadata:
  name: zap-pv
spec:
  capacity:
    storage: 1Gi
  accessModes:
    - ReadWriteOnce
  hostPath:
    path: /home/data/sonar
    type: DirectoryOrCreate
---
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: zap-pvc
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 1Gi
"""
}
