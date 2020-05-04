FROM adoptopenjdk/openjdk14:alpine-slim
WORKDIR /app
COPY target/uberjar/plugat-*-standalone.jar /app/plugat.jar
CMD ["java","-jar","plugat.jar"]