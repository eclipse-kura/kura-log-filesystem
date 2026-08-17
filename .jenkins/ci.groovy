@Library('add-ons-shared-libs@develop') _

node {
    continuousIntegrationPipeline(
        buildType: "deploy",
        sonar: [
            enable: false,
            projectKey: "eclipse-kura_kura-log-filesystem",
            tokenId: "sonarcloud-token-kura-log-filesystem",
            exclusions: "tests/**/*.java"
        ],
    )
}
