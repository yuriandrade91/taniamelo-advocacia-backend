#!/bin/bash
# Safe run script that ensures clean build and kills old processes

set -e

echo "🔥 Killing any running instances..."
pkill -f 'law-firm|LawFirmApplication' || true
sleep 1

echo "🧹 Cleaning and building project..."
mvn -q -DskipTests clean package

echo "🚀 Starting application..."
java -jar target/law-firm-0.0.1-SNAPSHOT.jar
