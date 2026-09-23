# Paved-road developer guide

## Prerequisites

- Java 21;
- an organization-provided Gradle init script that routes plugins and Maven
  artifacts exclusively through approved Artifactory repositories. The
  repository provides `gradle/modern-artifactory.init.gradle`, which reads the
  repository URLs and file-based credentials from the environment;
- Docker with Compose for runtime commands;
- approved internal image coordinates for the local infrastructure.

Keep credentials and trust material outside the repository as described in
[dependency-configuration.md](../dependency-configuration.md).

## Commands

```sh
./modern check
./modern build
./modern test
./modern up
./modern smoke
./modern down
```

`check` is offline and checks repository policy and Compose syntax when Docker
is installed. `build` and `test` use the Gradle wrapper with a workspace-local
cache. Set `MODERN_GRADLE_INIT_SCRIPT` to the approved init script.

The root Gradle lifecycle is authoritative for the complete monorepo:

```sh
./gradlew --init-script gradle/modern-artifactory.init.gradle build
./gradlew --init-script gradle/modern-artifactory.init.gradle test
./gradlew --init-script gradle/modern-artifactory.init.gradle sonar
```

`build` and `test` include OFBiz, every registered modern Java composite build,
the modern web artifacts, and repository architecture checks. `sonar` depends
on the complete test lifecycle and includes the modern services' compiled main
and test bytecode plus web, platform, infrastructure, local-development and
migration sources. A globally installed approved init script may omit the
explicit `--init-script` argument.

```sh
export MODERN_GRADLE_INIT_SCRIPT="$PWD/gradle/modern-artifactory.init.gradle"
export ARTIFACTORY_USER_FILE=/secure/path/username
export ARTIFACTORY_TOKEN_FILE=/secure/path/token
export ARTIFACTORY_MAVEN_URL=https://approved.example/maven
export ARTIFACTORY_GRADLE_PLUGIN_URL=https://approved.example/gradle-plugins
export CORPORATE_CA_FILE=/secure/path/corporate-ca.pem
```

Before `up`, copy `local-dev/images.env.example` to an ignored
`local-dev/images.env` and replace every placeholder with an approved internal
registry coordinate. `reset` removes Compose volumes and therefore requires
the literal confirmation `--confirm-delete-local-data`.

## Creating a service

1. Copy `services/template-service` to `services/<bounded-context>-service`.
2. Replace the template package, application name, ownership metadata, and
   OpenAPI title.
3. Add the service path to `platform/components.txt`.
4. Publish API/event schemas under `platform/contracts` before adding a
   consumer.
5. Run `./modern check test` before review.

Services are standalone builds. They may share technical contract/test assets,
but never OFBiz libraries, another service's domain model, or another service's
database.
