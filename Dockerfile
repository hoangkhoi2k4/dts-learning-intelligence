# Stage 1: Build ứng dụng
FROM maven:3.9-eclipse-temurin-17-alpine AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Runtime image
FROM eclipse-temurin:17-jre-alpine AS runtime
WORKDIR /app
RUN addgroup -S dts && adduser -S dts -G dts
USER dts
COPY --from=builder --chown=dts:dts /app/target/*.jar app.jar
EXPOSE 8091

ENTRYPOINT ["java", \
  "-XX:+UseSerialGC", \
  "-Xms128m", \
  "-Xmx256m", \
  "-XX:MaxMetaspaceSize=128m", \
  "-Xss512k", \
  "-jar", "app.jar"]