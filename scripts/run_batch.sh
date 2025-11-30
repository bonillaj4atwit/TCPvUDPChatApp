#!/usr/bin/env bash
# Usage: ./run_batch.sh experiments/configs
DIR=${1:-experiments/configs}

# Build using maven
mvn -q -DskipTests package

for f in "$DIR"/*.json; do
  echo "Running scenario $f"
  java -cp target/classes:$(mvn -q dependency:build-classpath -Dmdep.outputFile=/dev/stdout | tail -n 1) chat.experiments.BatchRunner "$f"
done
