# ---- Stage 1: Build the JAR ----
FROM maven:3.9.4-eclipse-temurin-17-alpine AS build
WORKDIR /app

# Copy pom first (for layer caching)
COPY pom.xml ./
RUN mvn dependency:go-offline -B

# Copy source and build
COPY src ./src
RUN mvn clean package -DskipTests -B

# ---- Stage 2: Run the JAR ----
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Render's free tier has 512MB RAM — JVM tuning is critical
ENV JAVA_TOOL_OPTIONS="-Xmx400m -Xms128m -XX:MaxMetaspaceSize=128m -XX:+UseSerialGC"

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]