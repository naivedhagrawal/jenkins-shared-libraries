def call() {
    podTemplate(
        agentContainer: 'jnlp',
        agentInjection: true,
        showRawYaml: false,
        containers: [
            containerTemplate(name: 'jnlp', image: 'jenkins/inbound-agent:latest', alwaysPullImage: true, privileged: true),
        ]
    )
}
