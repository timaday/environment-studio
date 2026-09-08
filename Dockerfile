# syntax=docker/dockerfile:1@sha256:ecfaec9ed6d810b56388c508f4121597bfbba70d41a6dfeee4d8cad5f295fc32
# Digests were observed in the first GitHub build; dependency PRs must requalify them.
ARG NODE_IMAGE=node:24-bookworm-slim@sha256:ba849c60be29959425b8734d57b8b4b7d56f98edd9504c9af091d5281095a71e
ARG MAVEN_IMAGE=maven:3.9.16-eclipse-temurin-21@sha256:8f6ac126f7810bb5549c4cd122d2bf0e9cda5bdeb0838aa928f09e779fd8bef8
ARG RUNTIME_IMAGE=eclipse-temurin:21-jre-jammy@sha256:eebd356ad7358b7094758e5787a6726f332917cfd56feab6457c56dab895cdbf

FROM ${NODE_IMAGE} AS ui
WORKDIR /build/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
COPY fixtures/ /build/fixtures/
COPY schemas/ /build/schemas/
COPY scripts/schema.test.mjs /build/scripts/schema.test.mjs
COPY docs/contracts/openapi-workspace-v2.json /build/docs/contracts/openapi-workspace-v2.json
RUN npm run check && npm test && npm run build

FROM ui AS browser-check
RUN npx playwright install --with-deps chromium
RUN npm run test:e2e

FROM ${MAVEN_IMAGE} AS java-build
WORKDIR /build
COPY backend/ ./backend/
COPY schemas/ ./schemas/
COPY fixtures/native-v2/ ./fixtures/native-v2/
COPY fixtures/profile-v2/ ./fixtures/profile-v2/
COPY fixtures/db-observation/ ./fixtures/db-observation/
COPY fixtures/structural-target/ ./fixtures/structural-target/
COPY deploy/HealthProbe.java /build/deploy/HealthProbe.java
COPY --from=ui /build/frontend/dist/ ./backend/server/src/main/resources/static/
RUN mvn -B -ntp -f backend/pom.xml verify
RUN javac -d /build/probe /build/deploy/HealthProbe.java
RUN mkdir -p /build/sqlite-native && cd /build/sqlite-native && \
    jar --extract --file /root/.m2/repository/org/xerial/sqlite-jdbc/3.53.4.0/sqlite-jdbc-3.53.4.0.jar \
    org/sqlite/native/Linux/x86_64/libsqlitejdbc.so

FROM ${RUNTIME_IMAGE} AS runtime
ARG SOURCE_REVISION=unknown
ARG SOURCE_URL=https://github.com/timaday/environment-studio
LABEL org.opencontainers.image.title="Environment Studio" \
      org.opencontainers.image.description="Deterministic environment configuration workbench — development starter" \
      org.opencontainers.image.source="${SOURCE_URL}" \
      org.opencontainers.image.revision="${SOURCE_REVISION}"
RUN groupadd --gid 10001 studio && useradd --uid 10001 --gid studio --no-create-home --shell /usr/sbin/nologin studio
WORKDIR /opt/studio
COPY --from=java-build --chown=10001:10001 /build/backend/server/target/environment-studio.jar /opt/studio/app.jar
COPY --from=java-build --chown=10001:10001 /build/probe/ /opt/studio/probe/
COPY --from=java-build /build/sqlite-native/org/sqlite/native/Linux/x86_64/libsqlitejdbc.so /opt/studio/native/libsqlitejdbc.so
USER 10001:10001
ENV STUDIO_MODE=demo
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 CMD ["java", "-cp", "/opt/studio/probe", "HealthProbe"]
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=65", "-XX:+ExitOnOutOfMemoryError", "-Djava.io.tmpdir=/tmp", "-Dorg.sqlite.lib.path=/opt/studio/native", "-jar", "/opt/studio/app.jar"]
