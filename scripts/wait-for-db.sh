#!/usr/bin/env bash
# wait-for-db.sh: wait for a host:port to be ready
set -e
host="$1"
port="$2"
shift 2
cmd="$@"

if [ -z "$host" ] || [ -z "$port" ]; then
  echo "Usage: $0 host port [command]"
  exit 2
fi

echo "Waiting for $host:$port..."
while ! nc -z "$host" "$port"; do
  sleep 1
done

echo "$host:$port is available"
if [ -n "$cmd" ]; then
  exec $cmd
fi
