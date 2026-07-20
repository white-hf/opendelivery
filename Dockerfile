# Stage 1: Runtime
FROM eclipse-temurin:17-jre

# Set deployment metadata
LABEL maintainer="EasyDelivery Developer"
LABEL description="EasyDelivery Monolithic Spring Boot Backend Service"

# Configure working directory
WORKDIR /app

# Add a non-root group and user for security hardening
RUN groupadd -r appgroup && useradd -r -g appgroup appuser
USER appuser

# Copy the packaged executable fat-jar into the container
COPY easydelivery-app/target/easydelivery-app-1.0.0.jar app.jar

# Expose backend service port (matching Android app config)
EXPOSE 9000

# Set active Spring profile or custom system properties
ENV JAVA_OPTS="-XX:+UseG1GC -XX:+ExitOnOutOfMemoryError"

# Container execution command
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
