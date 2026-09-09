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

# The <version> that follows an end-point-blank-java <artifactId>. That shape
# is shared by this app's dependency block and by the SDK's own project
# coordinates, so one reader serves both files.
sdk_version_in() {
    awk '/<artifactId>end-point-blank-java<\/artifactId>/ { found = 1; next }
         found && /<version>/ {
             gsub(/.*<version>|<\/version>.*/, ""); print; exit
         }' "$1"
}

# The SDK is not on Maven Central at this version (0.2.2 is the newest
# published), so the jar is vendored under lib/ and resolved from the local-lib
# repository. The version is READ from pom.xml rather than repeated here.
# It used to be a literal, and the same string was also spelled out in
# Dockerfile — three copies, nothing keeping them in sync, and the comment that
# stood here just asked the next person to remember. Deriving it means a pom
# bump cannot leave this script behind.
LIB_VERSION="$(sdk_version_in pom.xml)"
if [ -z "$LIB_VERSION" ]; then
    echo "build.sh: could not read the end-point-blank-java version from pom.xml" >&2
    exit 1
fi
LIB_JAR_DEST="lib/com/endpointblank/end-point-blank-java/${LIB_VERSION}/end-point-blank-java-${LIB_VERSION}.jar"

# Local dev only: if the end_point_blank_java source repo is checked out next
# to this one, rebuild the jar and refresh the vendored copy. CI/Render won't
# have the sibling repo and uses the checked-in jar.
# Overridable so the happy path can be pointed at a worktree without moving the
# sibling checkout off whatever branch its owner is working on.
LIB_SRC="${EPB_JAVA_SDK_SRC:-../end_point_blank_java}"
if [ -d "$LIB_SRC" ]; then
    # Refuse to vendor a jar that is not the one this build resolves.
    #
    # This block used to copy by name — `end-point-blank-java-${LIB_VERSION}.jar`
    # out of the sibling's target/ — after running `mvn package` without a
    # `clean`. target/ keeps every jar ever built there, so once the sibling's
    # pom moved past LIB_VERSION the build produced the new jar and the copy
    # silently took the leftover old one. `cp` succeeded, `set -o errexit` never
    # fired, and the stale jar was installed into ~/.m2 under the version the
    # pom asks for. The build went green against code that could be many commits
    # old, and it failed loudly only on a machine whose target/ had no
    # matching-version jar — which is the exact inverse of the machines the old
    # comment was worried about.
    SRC_VERSION="$(sdk_version_in "$LIB_SRC/pom.xml")"
    if [ "$SRC_VERSION" != "$LIB_VERSION" ]; then
        echo "build.sh: the SDK checkout at $LIB_SRC is version ${SRC_VERSION:-unreadable}," >&2
        echo "  but pom.xml asks for $LIB_VERSION. Refusing to vendor a jar that is not the" >&2
        echo "  one this build resolves. Check out the matching SDK revision, or move" >&2
        echo "  pom.xml to ${SRC_VERSION:-that version}." >&2
        exit 1
    fi

    # `clean`, so a jar left over from an earlier version cannot be picked up by
    # the copy below even if the version check above is ever loosened.
    (cd "$LIB_SRC" && mvn clean package -DskipTests -q)

    mkdir -p "$(dirname "$LIB_JAR_DEST")"
    cp "$LIB_SRC/target/end-point-blank-java-${LIB_VERSION}.jar" "$LIB_JAR_DEST"
fi

if [ ! -f "$LIB_JAR_DEST" ]; then
    echo "build.sh: no vendored jar at $LIB_JAR_DEST." >&2
    echo "  pom.xml asks for end-point-blank-java $LIB_VERSION; vendor that jar under lib/," >&2
    echo "  or check out the matching SDK next to this repo and re-run." >&2
    exit 1
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
