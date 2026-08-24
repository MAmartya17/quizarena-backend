# ---- Stage 1: Build the JAR ----
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app

# Copy maven wrapper and pom first (for layer caching)
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw
RUN ./mvnw dependency:go-offline -B

# Copy source and build
COPY src ./src
RUN ./mvnw clean package -DskipTests -B

# ---- Stage 2: Run the JAR ----
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Render's free tier has 512MB RAM — JVM tuning is critical
ENV JAVA_TOOL_OPTIONS="-Xmx400m -Xms128m -XX:MaxMetaspaceSize=128m -XX:+UseSerialGC"

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]