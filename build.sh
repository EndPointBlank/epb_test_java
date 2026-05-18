#!/usr/bin/env bash
set -o errexit

cd "$(dirname "$0")"

if [ ! -d "jdk" ]; then
    curl -L "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.7%2B6/OpenJDK21U-jdk_x64_linux_hotspot_21.0.7_6.tar.gz" -o jdk.tar.gz
    mkdir -p jdk
    tar -xzf jdk.tar.gz -C jdk --strip-components=1
    rm jdk.tar.gz
fi

export JAVA_HOME="$(pwd)/jdk"
export PATH="$JAVA_HOME/bin:$PATH"

# Install the vendored JAR into the local Maven repo so it overrides any cached version.
./mvnw install:install-file \
  -Dfile="lib/com/endpointblank/end-point-blank-java/0.1.0/end-point-blank-java-0.1.0.jar" \
  -DgroupId=com.endpointblank \
  -DartifactId=end-point-blank-java \
  -Dversion=0.1.0 \
  -Dpackaging=jar \
  -q

./mvnw clean package -DskipTests
