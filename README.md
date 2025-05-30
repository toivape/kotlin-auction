# Kotlin Auction

## Overview
This is a Kotlin-based project that uses Spring Boot and various other dependencies to create a web application. This project includes features such as data persistence, web interface, and validation.

## Prerequisites
- JDK 21
- Gradle
- Docker

## Building the Project
To build the project, run the following command:
```sh
./gradlew build
```

## Running the Application
To run the application, use the following command:
```sh
./gradlew bootRun
```

## Building the Docker Image
To build the Docker image, follow these steps:

1. Ensure the project is built:
    ```sh
    ./gradlew clean build
    ```

2. Build the Docker image:
    ```sh
    docker build -t nauction:latest .
    ```

## Running the Docker Container
To run the Docker container, use the following command:
```sh
docker run -p 8080:8080 nauction:latest
```

## License
This project is licensed under the MIT License.
```