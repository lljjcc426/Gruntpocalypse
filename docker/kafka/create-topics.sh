#!/bin/sh
set -eu

BOOTSTRAP_SERVER="${KAFKA_BOOTSTRAP_SERVER:-kafka:9092}"

until /opt/bitnami/kafka/bin/kafka-topics.sh --bootstrap-server "$BOOTSTRAP_SERVER" --list >/dev/null 2>&1; do
  sleep 2
done

for topic in \
  control.session.create \
  control.session.obfuscate \
  control.task.create \
  control.task.start
do
  /opt/bitnami/kafka/bin/kafka-topics.sh \
    --bootstrap-server "$BOOTSTRAP_SERVER" \
    --create \
    --if-not-exists \
    --topic "$topic" \
    --partitions 1 \
    --replication-factor 1
done
