# Template service

Standalone Java 21/Spring Boot service used to create a new bounded-context
service. It exposes liveness/readiness through Actuator, OpenAPI documentation,
a versioned example endpoint, and structured ECS console logs.

The build intentionally declares no repository. Use
`gradle/modern-artifactory.init.gradle`, which reads the Maven/plugin URLs and
username/token file paths from the environment; resolution fails closed when
Artifactory is unavailable.

```sh
MODERN_GRADLE_INIT_SCRIPT=/secure/path/modern-artifactory.init.gradle \
  ./gradlew test
```
