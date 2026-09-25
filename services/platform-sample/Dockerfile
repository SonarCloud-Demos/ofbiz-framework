FROM eclipse-temurin:17-jre
USER root
RUN apt-get update && apt-get install -y --no-install-recommends wget && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY build/libs/platform-sample-0.1.0-SNAPSHOT-all.jar app.jar
USER 10001
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
