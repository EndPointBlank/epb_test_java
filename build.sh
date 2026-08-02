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

# Must match the end-point-blank-java version in pom.xml. The SDK is not on
# Maven Central at this version (0.2.2 is the newest published), so the jar is
# vendored under lib/ and resolved from the local-lib repository. Bumping the
# pom without bumping this and re-vendoring the jar breaks every build that
# does not already have the artifact in ~/.m2 — which means CI and Render,
# but not the machine that did the bump.
LIB_VERSION="0.3.0"
LIB_JAR_DEST="lib/com/endpointblank/end-point-blank-java/${LIB_VERSION}/end-point-blank-java-${LIB_VERSION}.jar"

# Local dev only: if the end_point_blank_java source repo is checked out
# next to this one, rebuild the jar from master and refresh the vendored
# copy. CI/Render won't have the sibling repo and uses the checked-in jar.
LIB_SRC="../end_point_blank_java"
if [ -d "$LIB_SRC" ]; then
    (cd "$LIB_SRC" && mvn package -DskipTests -q)
    cp "$LIB_SRC/target/end-point-blank-java-${LIB_VERSION}.jar" "$LIB_JAR_DEST"
fi

# Install the vendored JAR into the local Maven repo so it overrides any cached version.
./mvnw install:install-file \
  -Dfile="$LIB_JAR_DEST" \
  -DgroupId=com.endpointblank \
  -DartifactId=end-point-blank-java \
  -Dversion="${LIB_VERSION}" \
  -Dpackaging=jar \
  -q

./mvnw clean package -DskipTests
