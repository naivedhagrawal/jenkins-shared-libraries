def call() {
    return [
        agentContainer: 'jnlp',
        agentInjection: true,
        showRawYaml: false,
        containers: [
            containerTemplate(name: 'jnlp', image: 'jenkins/inbound-agent:latest', alwaysPullImage: true, privileged: true),
            containerTemplate(
                name: 'docker',
                image: image,
                readinessProbe: [
                    exec: [
                        command: ['sh', '-c', 'ls -S /var/run/docker.sock']
                    ],
                    initialDelaySeconds: 5,
                    periodSeconds: 5
                ],
                command: ['sleep'],
                args: ['99d'],
                volumeMounts: [
                    volumeMount(name: 'docker-socket', mountPath: '/var/run')
                ]
            ),
            containerTemplate(
                name: 'docker-daemon',
                image: 'docker:dind',
                securityContext: [
                    privileged: true
                ],
                command: ['dockerd'],
                volumeMounts: [
                    volumeMount(name: 'docker-socket', mountPath: '/var/run')
                ]
            )
        ],
        volumes: [
            emptyDirVolume(mountPath: '/var/run', name: 'docker-socket')
        ]
    ]
}
