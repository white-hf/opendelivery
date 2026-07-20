#!/bin/bash

# EasyDelivery Backend Command-line Helper Script
MVN_BIN="./tools/apache-maven-3.9.8/bin/mvn"

case "$1" in
  test)
    echo "=== Running Unit Tests ==="
    $MVN_BIN test
    ;;
  build)
    echo "=== Compiling and Packaging ==="
    $MVN_BIN clean package -DskipTests
    ;;
  run)
    echo "=== Starting Spring Boot Backend (Local JVM) ==="
    if [ ! -f easydelivery-app/target/easydelivery-app-1.0.0.jar ]; then
      echo "Executable JAR not found! Building first..."
      $MVN_BIN clean package -DskipTests
    fi
    java -jar easydelivery-app/target/easydelivery-app-1.0.0.jar
    ;;
  docker-build)
    echo "=== Building Docker Image ==="
    if [ ! -f easydelivery-app/target/easydelivery-app-1.0.0.jar ]; then
      echo "Executable JAR not found! Compiling JAR before container build..."
      $MVN_BIN clean package -DskipTests
    fi
    docker-compose build
    ;;
  docker-up)
    echo "=== Starting Containerized Backend (Port 9000) ==="
    docker-compose up -d
    echo "Service is running in background. Container logs:"
    docker-compose logs -f
    ;;
  docker-down)
    echo "=== Stopping Containerized Backend ==="
    docker-compose down
    ;;
  *)
    echo "Usage: ./run.sh {test|build|run|docker-build|docker-up|docker-down}"
    echo "  test         - Execute JUnit 5 Unit Tests"
    echo "  build        - Compile and package local JAR"
    echo "  run          - Start local backend on port 9000"
    echo "  docker-build - Package JAR and compile Docker Image"
    echo "  docker-up    - Run containerized service in background"
    echo "  docker-down  - Stop containerized service"
    exit 1
    ;;
esac
