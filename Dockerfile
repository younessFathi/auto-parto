FROM maven:3.9.5-openjdk-21 AS build
COPY . .
RUN mvn clean package -DskipTests

FROM openjdk:21-jdk
COPY --from=build /target/auto-parto-0.0.1-SNAPSHOT.jar auto-parto.jar
EXPOSE 8080
ENTRYPOINT ["java" , "-jar" , "auto-parto.jar"]

