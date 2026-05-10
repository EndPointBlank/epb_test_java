#!/usr/bin/env bash
set -o errexit

cd "$(dirname "$0")"

if ! command -v java &> /dev/null; then
    apt-get update -y
    apt-get install -y openjdk-21-jdk
    export JAVA_HOME=$(dirname $(dirname $(readlink -f $(which java))))
fi

./mvnw clean package -DskipTests
