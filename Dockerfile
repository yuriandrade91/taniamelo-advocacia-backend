FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace
# Carimba o SHA do deploy em /actuator/info - mesmo mecanismo do deploy nativo
# antigo (ver pom.xml, propriedade git.commit). Sem --build-arg, cai no
# default do pom ("desconhecido"), então builds locais continuam funcionando.
ARG GIT_COMMIT=desconhecido
COPY pom.xml checkstyle.xml ./
COPY src ./src
# Use BuildKit cache for Maven repository to speed up builds when BuildKit is enabled.
# Requires Docker BuildKit (usually enabled by default in modern Docker Desktop).
RUN --mount=type=cache,target=/root/.m2 \
		if [ -f ./mvnw ]; then \
			chmod +x ./mvnw && ./mvnw -DskipTests -Dgit.commit=$GIT_COMMIT package; \
		else \
			mvn -DskipTests -Dgit.commit=$GIT_COMMIT package; \
		fi

FROM eclipse-temurin:25-jre
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
