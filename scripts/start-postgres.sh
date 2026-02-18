#!/usr/bin/env bash
set -euo pipefail

LOG=/tmp/lawfirm.log
JAR=target/law-firm-0.0.1-SNAPSHOT.jar
PROFILE=postgres

if [ ! -f "$JAR" ]; then
  echo "Jar not found: $JAR"
  echo "Build the project first: ./mvnw -DskipTests package"
  exit 2
fi

# free port 8080 if occupied
PIDS=$(lsof -ti :8080 || true)
if [ -n "$PIDS" ]; then
  echo "Killing processes on port 8080: $PIDS"
  kill -9 $PIDS || true
  sleep 1
fi

# start application
echo "Starting $JAR with profile $PROFILE (logs -> $LOG)"
nohup java -Dspring.profiles.active=$PROFILE -jar "$JAR" > "$LOG" 2>&1 &
PID=$!
echo "Started PID: $PID"

# wait for startup message
TIMEOUT=60
i=0
while ! grep -q "Started LawFirmApplication" "$LOG"; do
  sleep 1
  i=$((i+1))
  if [ $i -ge $TIMEOUT ]; then
    echo "Timeout waiting for application to start (seen last $TIMEOUT seconds). Showing last 100 lines of log:" >&2
    tail -n 100 "$LOG" >&2
    exit 3
  fi
done

echo "Application started. Showing last 80 lines of log:"
tail -n 80 "$LOG"

echo "Performing quick smoke test: GET /api/clients"
HTTP_OUTPUT=$(curl -sS -w "\nHTTP_STATUS:%{http_code}\n" -X GET "http://localhost:8080/api/clients" -H "Accept: application/json")
echo "$HTTP_OUTPUT"

exit 0
