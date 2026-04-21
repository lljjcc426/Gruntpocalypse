ARG JAVA_IMAGE=mcr.microsoft.com/openjdk/jdk:21-mariner

FROM ${JAVA_IMAGE}

WORKDIR /app

COPY grunt-main/build/libs/grunt-main-all.jar /app/grunt-main-all.jar

EXPOSE 8081

ENTRYPOINT ["java", "--enable-preview", "-jar", "/app/grunt-main-all.jar"]
CMD ["--web", "--port=8081"]
