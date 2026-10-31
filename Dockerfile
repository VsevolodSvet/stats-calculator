# Build
FROM maven:3.9.11-eclipse-temurin-21 AS build
COPY pom.xml /app/
COPY src /app/src
RUN mvn -B -ntp -f /app/pom.xml clean package

# Run
FROM eclipse-temurin:21-jre
COPY --from=build /app/target/*.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]