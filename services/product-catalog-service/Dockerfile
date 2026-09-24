# syntax=docker/dockerfile:1.7
ARG JAVA_BUILD_IMAGE
ARG JAVA_RUNTIME_IMAGE
FROM ${JAVA_BUILD_IMAGE} AS build
ARG ARTIFACTORY_MAVEN_URL
ARG ARTIFACTORY_GRADLE_PLUGIN_URL
WORKDIR /workspace
COPY gradle/ gradle/
COPY services/product-catalog-service/settings.gradle services/product-catalog-service/build.gradle services/product-catalog-service/gradle.properties ./
COPY services/product-catalog-service/src/ src/
RUN --mount=type=secret,id=artifactory_user,target=/run/secrets/artifactory-user,required=true \
    --mount=type=secret,id=artifactory_token,target=/run/secrets/artifactory-token,required=true \
    --mount=type=secret,id=corporate_ca,target=/run/secrets/corporate-ca.pem,required=true \
    cp "$JAVA_HOME/lib/security/cacerts" /tmp/modern-truststore \
    && keytool -importcert -noprompt -storepass changeit -alias corporate-ca \
        -file /run/secrets/corporate-ca.pem -keystore /tmp/modern-truststore \
    && ARTIFACTORY_USER_FILE=/run/secrets/artifactory-user \
       ARTIFACTORY_TOKEN_FILE=/run/secrets/artifactory-token \
       ARTIFACTORY_MAVEN_URL="$ARTIFACTORY_MAVEN_URL" \
       ARTIFACTORY_GRADLE_PLUGIN_URL="$ARTIFACTORY_GRADLE_PLUGIN_URL" \
       GRADLE_OPTS="-Djavax.net.ssl.trustStore=/tmp/modern-truststore -Djavax.net.ssl.trustStorePassword=changeit" \
       gradle --no-daemon --init-script gradle/modern-artifactory.init.gradle test bootJar
FROM ${JAVA_RUNTIME_IMAGE}
RUN addgroup --system modern && adduser --system --ingroup modern modern
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar
USER modern
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
