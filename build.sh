#!/usr/bin/env bash
set -o errexit

cd "$(dirname "$0")"

if ! command -v java &> /dev/null; then
    curl -s "https://get.sdkman.io" | bash
    source "$HOME/.sdkman/bin/sdkman-init.sh"
    sdk install java 21.0.7-tem
    export JAVA_HOME="$HOME/.sdkman/candidates/java/current"
    export PATH="$JAVA_HOME/bin:$PATH"
fi

./mvnw clean package -DskipTests
