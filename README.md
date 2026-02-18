# taniamelo-advocacia-backend
Java application

## Run (build + run)

**Recommended approach for local development:**

```bash
# Build (skip tests for speed) and run the generated jar
mvn -DskipTests clean package && java -jar target/law-firm-0.0.1-SNAPSHOT.jar
```

**Alternative: run via Maven (auto-recompiles on changes):**

```bash
mvn spring-boot:run
```

## Important Notes

### DevTools and MapStruct
- **DevTools automatic restart is DISABLED** to avoid classloader issues with MapStruct-generated classes.
- When running from VS Code/IDE, the Java extension may use a different classloader that doesn't see generated annotation-processor sources.
- **Solution:** Always run the build task (Cmd+Shift+B or Terminal → Run Build Task: `mvn: build (generate sources)`) before starting the app from the IDE, OR use the terminal commands above.

### CORS
- CORS is permissive for development (allows all origins). **Restrict origins before deploying to production.**

### Database
- PostgreSQL required on `localhost:5432` (database: `system`, user: `postgres`, password: `postgres`).
- You can start a local Postgres instance using the script: `./scripts/start-postgres.sh` (if available).

