FROM eclipse-temurin:17-jdk AS build
WORKDIR /src
COPY services/notification-service/src/main/java/com/company/erp/notifications/NotificationApplication.java .
RUN javac -d /classes NotificationApplication.java
FROM eclipse-temurin:17-jre
RUN useradd --system --uid 10001 app
USER app
COPY --from=build /classes /classes
EXPOSE 18101
ENTRYPOINT ["java","-cp","/classes","com.company.erp.notifications.NotificationApplication"]
