FROM maven:3.8.8-eclipse-temurin-21-alpine AS build
COPY . .
RUN mvn clean package -DskipTests

FROM openjdk:21-jdk
COPY --from=build /target/auto-parto-0.0.1-SNAPSHOT.jar auto-parto.jar
EXPOSE 8080
ENTRYPOINT ["java" , "-jar" , "auto-parto.jar" , "-Dspring.profiles.active=dev"]

