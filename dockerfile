FROM eclipse-temurin:21-jdk-alpine
VOLUME /tmp
COPY target/*.jar app.jar
ENTRYPOINT ["java", "-Xmx400m", "-Xms200m", "-jar", "/app.jar"]