FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B verify

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S packplan && adduser -S packplan -G packplan
COPY --from=build /app/target/packplan-0.0.1-SNAPSHOT.jar app.jar
USER packplan
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
