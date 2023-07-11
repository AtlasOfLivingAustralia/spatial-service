FROM openjdk:11-jre-slim
COPY ./build/libs/spatial-service-*.war app.war
CMD ["java", "-jar", "-Dserver.port=8080", "app.war"]