# -------- Build Stage --------
FROM gradle:8.14.4-jdk21-alpine AS builder

WORKDIR /app
COPY . .

RUN gradle --no-daemon bootJar

# -------- Runtime Stage --------
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy only the bootable JAR and rename to app.jar
COPY --from=builder /app/build/libs/*.jar app.jar

RUN addgroup -S app && adduser -S app -G app
USER app

EXPOSE 8080

ENTRYPOINT ["java", "-XX:InitialRAMPercentage=20.0", "-XX:MaxRAMPercentage=60.0", "-jar", "app.jar"]
