# ── Стадия 1: сборка jar ───────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build
COPY pom.xml .
# Скачать зависимости заранее (слой кэшируется, если pom.xml не менялся)
RUN mvn dependency:go-offline -q

COPY src ./src
RUN mvn package -DskipTests -q

# ── Стадия 2: минимальный runtime-образ ───────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

# curl нужен для healthcheck в compose
RUN apk add --no-cache curl

WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar

# Профиль docker — application-docker.yaml
ENV SPRING_PROFILES_ACTIVE=docker
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
