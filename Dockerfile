FROM eclipse-temurin:21.0.8_9-jre-alpine-3.22

WORKDIR /app

ARG JAR_FILE
COPY ${JAR_FILE} app.jar

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom -Duser.timezone=Europe/Paris -Dfile.encoding=UTF8"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]