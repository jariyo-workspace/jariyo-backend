FROM gradle:8.14.3-jdk17 AS build

WORKDIR /workspace

COPY gradle gradle
COPY gradlew gradlew
COPY gradlew.bat gradlew.bat
COPY build.gradle settings.gradle ./
COPY src src

RUN ./gradlew --no-daemon bootJar -x test

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY --from=build /workspace/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
