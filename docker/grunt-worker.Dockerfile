ARG JAVA_IMAGE=mcr.microsoft.com/openjdk/jdk:21-mariner

FROM ${JAVA_IMAGE}

WORKDIR /app

COPY grunt-back/build/libs/grunt-back.jar /app/grunt-back.jar

EXPOSE 8080

ENTRYPOINT ["java", "--enable-preview", "-jar", "/app/grunt-back.jar"]
