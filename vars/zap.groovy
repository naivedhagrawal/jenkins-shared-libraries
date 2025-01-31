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
        claimName: zap-pv-claim
---
kind: StorageClass
apiVersion: storage.k8s.io/v1
metadata:
  name: local-storage-kubepod
provisioner: kubernetes.io/no-provisioner
volumeBindingMode: WaitForFirstConsumer

#Persistent Volume
---
apiVersion: v1
kind: PersistentVolume
metadata:
  name: zap-pv-volume
  labels:
    type: local
spec:
  storageClassName: local-storage-kubepod
  claimRef:
    name: zap-pv-claim
    namespace: devops-tools
  capacity:
    storage: 1Gi
  accessModes:
    - ReadWriteMany
  local:
    path: /home/data/jenkins
  nodeAffinity:
    required:
      nodeSelectorTerms:
      - matchExpressions:
        - key: kubernetes.io/hostname
          operator: In
          values:
          - kind-worker

#Persistent Volume Claim
---
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: zap-pv-claim
  namespace: devops-tools
spec:
  storageClassName: local-storage-kubepod
  accessModes:
    - ReadWriteMany
  resources:
    requests:
      storage: 1Gi
"""
}
