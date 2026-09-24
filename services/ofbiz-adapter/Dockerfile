FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S modern && adduser -S modern -G modern
COPY build/libs/*-all.jar /app/app.jar
USER modern
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
