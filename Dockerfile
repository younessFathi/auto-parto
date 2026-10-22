# Dockerfile - Optimized for Java 21 with Maven cache
FROM maven:3.8.8-eclipse-temurin-21-alpine AS build

# Create working directory
WORKDIR /app

# 1. Copy only the POM files first (this layer is cached)
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .

# 2. Download dependencies (cached unless pom.xml changes)
# Using dependency:resolve instead of dependency:go-offline for better reliability
RUN mvn dependency:resolve

# 3. Copy source code
COPY src ./src

# 4. Build the application
RUN mvn clean package -DskipTests

# 5. Runtime stage - lightweight JRE
FROM eclipse-temurin:21-jre-alpine AS runtime

# Install wget for health checks
RUN apk add --no-cache wget

# Create non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Set working directory
WORKDIR /app

# Copy jar from build stage
COPY --from=build --chown=spring:spring /app/target/auto-parto-*.jar app.jar

# Expose port
EXPOSE 8080

# Let the JVM receive the termination signal so Spring can drain in-flight work.
STOPSIGNAL SIGTERM

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 CMD wget -qO- http://localhost:8080/actuator/health || exit 1

# Use ENTRYPOINT for exec form, CMD for arguments
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
