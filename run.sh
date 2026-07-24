#!/bin/bash

# EasyDelivery Backend Command-line Helper Script
MVN_BIN="./tools/apache-maven-3.9.8/bin/mvn"

case "$1" in
  test)
    echo "=== Running Unit Tests ==="
    $MVN_BIN test
    ;;
  build)
    echo "=== Compiling and Packaging All Services ==="
    $MVN_BIN clean package -DskipTests
    ;;
  run)
    echo "=== Starting Operations API Service (Port 9001) ==="
    if [ ! -f operations/easydelivery-ops-api/target/easydelivery-ops-api-1.0.0.jar ]; then
      echo "Operations API JAR not found! Building first..."
      $MVN_BIN clean package -DskipTests
    fi
    java -jar operations/easydelivery-ops-api/target/easydelivery-ops-api-1.0.0.jar
    ;;
  run-driver)
    echo "=== Starting Driver API Service (Port 9000) ==="
    if [ ! -f driver/easydelivery-driver-api/target/easydelivery-driver-api-1.0.0.jar ]; then
      echo "Driver API JAR not found! Building first..."
      $MVN_BIN clean package -DskipTests
    fi
    java -jar driver/easydelivery-driver-api/target/easydelivery-driver-api-1.0.0.jar
    ;;
  run-ops)
    echo "=== Starting Operations API Service (Port 9001) ==="
    if [ ! -f operations/easydelivery-ops-api/target/easydelivery-ops-api-1.0.0.jar ]; then
      echo "Operations API JAR not found! Building first..."
      $MVN_BIN clean package -DskipTests
    fi
    java -jar operations/easydelivery-ops-api/target/easydelivery-ops-api-1.0.0.jar
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
    echo "Usage: ./run.sh {test|build|run|run-driver|run-ops|docker-build|docker-up|docker-down}"
    echo "  test         - Execute JUnit 5 Unit Tests"
    echo "  build        - Compile and package local JARs"
    echo "  run          - Start local monolith backend (port 9000)"
    echo "  run-driver   - Start Driver API service (port 9000)"
    echo "  run-ops      - Start Operations API service (port 9001)"
    echo "  docker-build - Package JAR and compile Docker Image"
    echo "  docker-up    - Run containerized service in background"
    echo "  docker-down  - Stop containerized service"
    exit 1
    ;;
esac
