# syntax=docker/dockerfile:1
FROM gradle:8.14.3-jdk17 AS build
WORKDIR /workspace
COPY services/accounting-invoice-service/ ./
RUN --mount=type=cache,id=accounting-gradle-cache,target=/home/gradle/.gradle gradle --no-daemon bootJar

FROM eclipse-temurin:17-jre
RUN useradd --system --uid 10001 app
USER app
COPY --from=build /workspace/build/libs/accounting-invoice-service-*.jar /app.jar
EXPOSE 18106
ENTRYPOINT ["java","-jar","/app.jar"]
