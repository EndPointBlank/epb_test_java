#!/usr/bin/env bash
set -o errexit

cd "$(dirname "$0")"

# Vendored ./jdk is a Linux build used on Render. Only download / activate it
# when actually on Linux; on macOS we rely on the system Java instead.
if [ "$(uname -s)" = "Linux" ]; then
    if [ ! -d "jdk" ]; then
        curl -L "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.7%2B6/OpenJDK21U-jdk_x64_linux_hotspot_21.0.7_6.tar.gz" -o jdk.tar.gz
        mkdir -p jdk
        tar -xzf jdk.tar.gz -C jdk --strip-components=1
        rm jdk.tar.gz
    fi

    export JAVA_HOME="$(pwd)/jdk"
    export PATH="$JAVA_HOME/bin:$PATH"
fi

# Local dev only: create the database if it doesn't exist. Hibernate
# (`spring.jpa.hibernate.ddl-auto=update`) creates / migrates tables on
# startup, so no explicit migration step is needed here.
if [ -z "$DATABASE_URL" ]; then
    PGPASSWORD="${PGPASSWORD:-postgres}" createdb \
        -h "${PGHOST:-localhost}" \
        -U "${PGUSER:-postgres}" \
        ejb_test_java_development 2>/dev/null || true
fi

LIB_JAR_DEST="lib/com/endpointblank/end-point-blank-java/0.1.0/end-point-blank-java-0.1.0.jar"

# Local dev only: if the end_point_blank_java source repo is checked out
# next to this one, rebuild the jar from master and refresh the vendored
# copy. CI/Render won't have the sibling repo and uses the checked-in jar.
LIB_SRC="../end_point_blank_java"
if [ -d "$LIB_SRC" ]; then
    (cd "$LIB_SRC" && mvn package -DskipTests -q)
    cp "$LIB_SRC/target/end-point-blank-java-0.1.0.jar" "$LIB_JAR_DEST"
fi

# Install the vendored JAR into the local Maven repo so it overrides any cached version.
./mvnw install:install-file \
  -Dfile="$LIB_JAR_DEST" \
  -DgroupId=com.endpointblank \
  -DartifactId=end-point-blank-java \
  -Dversion=0.1.0 \
  -Dpackaging=jar \
  -q

./mvnw clean package -DskipTests
