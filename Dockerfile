# Build Stage
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests

# Run Stage
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Metadatos del microservicio
LABEL maintainer="Anthony Rojas <anthonyatras@gmail.com>"
LABEL version="1.0.0"
LABEL description="JCE Consulta API Microservice"

# Configuración de rendimiento de la JVM para contenedores
ENV JAVA_OPTS="-XX:+UseParallelGC -XX:MaxRAMPercentage=75.0 -XX:MinRAMPercentage=50.0 -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
