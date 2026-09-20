FROM gradle:8.14-jdk17 AS build
WORKDIR /workspace
COPY settings.gradle build.gradle ./
COPY src src
RUN --mount=type=cache,id=accounting-invoice-gradle,target=/home/gradle/.gradle/caches \
    --mount=type=secret,id=gradle_properties,target=/home/gradle/.gradle/gradle.properties,uid=1000,required=true \
    --mount=type=secret,id=corporate_ca,target=/run/secrets/corporate-ca.pem,uid=1000,required=true \
    cp "$JAVA_HOME/lib/security/cacerts" /tmp/modern-cacerts \
    && keytool -importcert -noprompt -alias corporate-inspection -file /run/secrets/corporate-ca.pem \
       -keystore /tmp/modern-cacerts -storepass changeit \
    && gradle bootJar --no-daemon -Djavax.net.ssl.trustStore=/tmp/modern-cacerts \
       -Djavax.net.ssl.trustStorePassword=changeit \
    && rm /tmp/modern-cacerts

FROM eclipse-temurin:17-jre
RUN useradd --system --uid 10001 modern
USER modern
WORKDIR /app
COPY --from=build /workspace/build/libs/accounting-invoice-service-0.1.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
