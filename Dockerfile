FROM maven:3.8.8-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
# Use BuildKit cache for Maven repository to speed up builds when BuildKit is enabled.
# Requires Docker BuildKit (usually enabled by default in modern Docker Desktop).
RUN --mount=type=cache,target=/root/.m2 \
		if [ -f ./mvnw ]; then \
			chmod +x ./mvnw && ./mvnw -DskipTests package; \
		else \
			mvn -DskipTests package; \
		fi

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/target/*.jar /app/app.jar
COPY scripts/wait-for-db.sh /app/wait-for-db.sh
RUN chmod +x /app/wait-for-db.sh
# Install netcat (nc) and curl needed by wait-for-db.sh and healthchecks
RUN apt-get update \
	&& apt-get install -y --no-install-recommends netcat-openbsd curl \
	&& rm -rf /var/lib/apt/lists/*
EXPOSE 8080
ENTRYPOINT ["/app/wait-for-db.sh", "db", "5432", "java", "-jar", "/app/app.jar"]
