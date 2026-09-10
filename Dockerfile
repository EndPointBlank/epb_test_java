# Built and run per test run (not a long-lived service) on a t4g.small
# staging box already hosting app_portal, intake, Caddy and Mailpit — the
# image needs to be small and start fast, not permanently resident.
#
# Base images below are chosen for their multi-arch (amd64 + arm64) manifests
# so the platform resolves the right variant on Graviton without pinning arch.

# --- build stage: needs the full JDK + Maven, discarded after packaging ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# The end-point-blank-java SDK isn't on Maven Central at this version, so it's
# vendored under lib/ (see build.sh). Install it into the local repo the same
# way build.sh does, before resolving the rest of the dependency tree, so a
# source-only edit later doesn't invalidate this layer.
COPY lib/ lib/
# The version is read from pom.xml, not repeated here. pom.xml, build.sh and
# this file each used to carry their own copy of the string with nothing
# keeping them in sync, so a pom bump could leave two of the three behind and
# the image would install a jar the build no longer resolves.
RUN set -eu; \
    SDK_VERSION="$(awk '/<artifactId>end-point-blank-java<\/artifactId>/ { found = 1; next } \
                        found && /<version>/ { gsub(/.*<version>|<\/version>.*/, ""); print; exit }' pom.xml)"; \
    if [ -z "$SDK_VERSION" ]; then \
      echo "Dockerfile: could not read the end-point-blank-java version from pom.xml" >&2; \
      exit 1; \
    fi; \
    ./mvnw install:install-file \
      -Dfile="lib/com/endpointblank/end-point-blank-java/${SDK_VERSION}/end-point-blank-java-${SDK_VERSION}.jar" \
      -DgroupId=com.endpointblank \
      -DartifactId=end-point-blank-java \
      -Dversion="${SDK_VERSION}" \
      -Dpackaging=jar \
      -q; \
    ./mvnw dependency:go-offline -q

COPY src/ src/
RUN ./mvnw clean package -DskipTests -q

# --- runtime stage: JRE only, no Maven/JDK/build tools shipped ---
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

COPY --from=build /app/target/ejb-test-java-0.1.0.jar app.jar

# server.port defaults to 3001 (application.properties); Spring Boot's
# relaxed env binding lets SERVER_PORT override it at runtime if ever needed.
EXPOSE 3001

# All app config (INTAKE_API_URL, DATABASE_URL, GIT_COMMIT, ...) comes from
# the runtime environment — nothing environment-specific is baked in here.
CMD ["java", "-jar", "app.jar"]
