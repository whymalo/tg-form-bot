FROM eclipse-temurin:24-jre
WORKDIR /app

COPY target/telegram-bot-0.0.1-SNAPSHOT.jar app.jar

ENV TZ=Europe/Moscow

ENTRYPOINT ["java","-jar","/app/app.jar"]
