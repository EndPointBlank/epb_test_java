#!/usr/bin/env bash
set -o errexit

cd "$(dirname "$0")"

if [ ! -d "$HOME/.sdkman" ]; then
    curl -s "https://get.sdkman.io" | bash
fi
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install java 21.0.7-tem 2>/dev/null || true
export JAVA_HOME="$HOME/.sdkman/candidates/java/current"
export PATH="$JAVA_HOME/bin:$PATH"

./mvnw clean package -DskipTests
