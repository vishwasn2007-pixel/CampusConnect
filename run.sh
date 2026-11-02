#!/usr/bin/env bash
# Compile and start CampusConnect (macOS / Linux)
cd "$(dirname "$0")" || exit 1

mkdir -p backend/out
find backend/src -name "*.java" > backend/sources.txt

echo "Compiling..."
javac -encoding UTF-8 -cp "backend/lib/*" -d backend/out @backend/sources.txt || { echo "Compilation failed."; exit 1; }

echo "Starting server..."
java -cp "backend/out:backend/lib/*" Main
