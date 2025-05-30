FROM amazoncorretto:21-alpine-jdk
VOLUME /tmp
COPY build/libs/kotlin-auction-*-SNAPSHOT.jar app.jar
ENTRYPOINT ["java","-jar","/app.jar"]
