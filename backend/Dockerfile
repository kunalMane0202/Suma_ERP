# syntax=docker/dockerfile:1
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -DskipTests

FROM eclipse-temurin:21-jre
RUN groupadd --system suma && useradd --system --gid suma --home /app suma
WORKDIR /app
COPY --from=build /src/target/suma-tracking-tool-*.jar app.jar
RUN mkdir -p /app/uploads && chown -R suma:suma /app
USER suma
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
