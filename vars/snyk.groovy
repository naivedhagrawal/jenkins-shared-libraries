// @Library('Shared-Libraries') _
// Fuction call --> snyk(arguments)

def call(String projectType, boolean runImageScan = false, String imageName = '', boolean runIacScan = false) {
    def snykImage = ''
    switch (projectType) {
        case 'maven':
            snykImage = 'snyk/snyk:maven'
            break
        case 'node':
            snykImage = 'snyk/snyk:node'
            break
        case 'python':
            snykImage = 'snyk/snyk:python'
            break
        default:
            error "Unsupported project type: ${projectType}"
    }
    def scanCommands = """
        snyk test
    """
    if (runImageScan) {
        scanCommands += """
        snyk test --docker ${imageName}
        """
    }
    if (runIacScan) {
        scanCommands += """
        snyk iac test
        """
    }
    return """
        apiVersion: v1
        kind: Pod
        spec:
          containers:
          - name: snyk
            image: ${snykImage}
            imagePullPolicy: Always
            command:
            - cat
            tty: true
          - name: scanner
            image: snyk/snyk-cli
            imagePullPolicy: Always
            command:
            - sh
            - -c
            - |
              withCredentials([string(credentialsId: 'SNYK_TOKEN', variable: 'SNYK_TOKEN')]) {
                  try {
                      snyk auth \\$SNYK_TOKEN
                      ${scanCommands}
                  } catch (Exception e) {
                      echo "Snyk scan failed: \${e.getMessage()}"
                      currentBuild.result = 'FAILURE'
                  }
              }
            tty: true
        """
}





/* Pipeline usage
            
*/


/* Images
Current images
Image	Based on
snyk/snyk:alpine	alpine
snyk/snyk:cocoapods	alpine
snyk/snyk:swift	swift
snyk/snyk:clojure	clojure
snyk/snyk:clojure-boot	clojure:boot
snyk/snyk:clojure-lein	clojure:lein
snyk/snyk:clojure-tools-deps	clojure:tools-deps
snyk/snyk:composer	composer
snyk/snyk:php	composer
snyk/snyk:docker-latest	docker:latest
snyk/snyk:docker	docker:stable
snyk/snyk:golang	golang
snyk/snyk:golang-1.20	golang:1.20
snyk/snyk:golang-1.21	golang:1.21
snyk/snyk:golang-1.22	golang:1.22
snyk/snyk:golang-1.23	golang:1.23
snyk/snyk:gradle	gradle
snyk/snyk:gradle-jdk11	gradle:jdk11
snyk/snyk:gradle-jdk12	gradle:jdk12
snyk/snyk:gradle-jdk13	gradle:jdk13
snyk/snyk:gradle-jdk14	gradle:jdk14
snyk/snyk:gradle-jdk16	gradle:jdk16
snyk/snyk:gradle-jdk17	gradle:jdk17
snyk/snyk:gradle-jdk18	gradle:jdk18
snyk/snyk:gradle-jdk19	gradle:jdk19
snyk/snyk:gradle-jdk20	gradle:jdk20
snyk/snyk:gradle-jdk21	gradle:jdk21
snyk/snyk:gradle-jdk8	gradle:jdk8
snyk/snyk:maven	maven
snyk/snyk:maven-3-jdk-11	maven:3-jdk-11
snyk/snyk:maven-3-jdk-17	maven:3-eclipse-temurin-17
snyk/snyk:maven-3-jdk-20	maven:3-eclipse-temurin-20
snyk/snyk:maven-3-jdk-21	maven:3-eclipse-temurin-21
snyk/snyk:maven-3-jdk-22	maven:3-eclipse-temurin-22
snyk/snyk:maven-3-jdk-8	maven:3-jdk-8
snyk/snyk:dotnet	mcr.microsoft.com/dotnet/core/sdk
snyk/snyk:dotnet-8.0	mcr.microsoft.com/dotnet/sdk:8.0
snyk/snyk:node	node
snyk/snyk:node-18	node:18
snyk/snyk:node-20	node:20
snyk/snyk:node-22	node:22
snyk/snyk:python	python
snyk/snyk:python-3.8	python:3.8
snyk/snyk:python-3.9	python:3.9
snyk/snyk:python-3.10	python:3.10
snyk/snyk:python-3.11	python:3.11
snyk/snyk:python-3.12	python:3.12
snyk/snyk:python-alpine	python:alpine
snyk/snyk:ruby	ruby
snyk/snyk:ruby-3.3	ruby:3.3
snyk/snyk:ruby-alpine	ruby:alpine
snyk/snyk:linux	ubuntu
snyk/snyk:sbt1.10.0-scala3.4.2	scala:3.4.2-sbt:1.10.0
Vendor unsupported base images
These images are no longer supported by the upstream vendor and should no longer be used, as such, the images below are no longer maintained. As a general practice, Snyk does not remove images once published. However, Snyk will not build or maintain images based on EoL softwareusers of these images should move to a vendor-supported upstream image base immediately.

Image	Based on
snyk/snyk:docker-18.09	docker:18.09
snyk/snyk:docker-19.03	docker:19.03
snyk/snyk:golang-1.12	golang:1.12
snyk/snyk:golang-1.13	golang:1.13
snyk/snyk:golang-1.14	golang:1.14
snyk/snyk:golang-1.15	golang:1.15
snyk/snyk:golang-1.16	golang:1.16
snyk/snyk:golang-1.17	golang:1.17
snyk/snyk:golang-1.18	golang:1.18
snyk/snyk:golang-1.19	golang:1.19
snyk/snyk:gradle-6.4	gradle:6.4
snyk/snyk:gradle-6.4-jdk11	gradle:6.4-jdk11
snyk/snyk:gradle-6.4-jdk14	gradle:6.4-jdk14
snyk/snyk:gradle-6.4-jdk8	gradle:6.4-jdk8
snyk/snyk:maven-3-jdk-19	maven:3-eclipse-temurin-19
snyk/snyk:dotnet-2.1	mcr.microsoft.com/dotnet/core/sdk:2.1
snyk/snyk:dotnet-2.2	mcr.microsoft.com/dotnet/core/sdk:2.2
snyk/snyk:dotnet-3.0	mcr.microsoft.com/dotnet/core/sdk:3.0
snyk/snyk:dotnet-3.1	mcr.microsoft.com/dotnet/core/sdk:3.1
snyk/snyk:node-8	node:8
snyk/snyk:node-10	node:10
snyk/snyk:node-12	node:12
snyk/snyk:node-13	node:13
snyk/snyk:node-14	node:14
snyk/snyk:node-15	node:15
snyk/snyk:node-16	node:16
snyk/snyk:python-2.7	python:2.7
snyk/snyk:python-3.6	python:3.6
snyk/snyk:python-3.7	python:3.7
snyk/snyk:ruby-2.4	ruby:2.4
snyk/snyk:ruby-2.5	ruby:2.5
snyk/snyk:ruby-2.6	ruby:2.6
snyk/snyk:ruby-2.7	ruby:2.7
snyk/snyk:sbt	hseeberger/scala-sbt:8u212_1.2.8_2.13.0
snyk/snyk:scala	hseeberger/scala-sbt:8u212_1.2.8_2.13.0 */