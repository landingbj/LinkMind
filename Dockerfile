FROM eclipse-temurin:8-jre-jammy

LABEL org.opencontainers.image.source="https://github.com/landingbj/LinkMind"

ENV SERVER_ADDRESS=0.0.0.0 \
    SERVER_PORT=8080 \
    CONFIG_DIR=/opt/linkmind/config \
    DATA_DIR=/var/lib/linkmind/data \
    JAVA_OPTS="-Xms512m -Xmx1024m"

WORKDIR /opt/linkmind

RUN groupadd --system linkmind \
    && useradd --system --gid linkmind --home-dir /opt/linkmind linkmind \
    && mkdir -p /opt/linkmind/config /var/lib/linkmind/data \
    && chown -R linkmind:linkmind /opt/linkmind /var/lib/linkmind

COPY --chown=linkmind:linkmind lagi-web/target/LinkMind.jar /opt/linkmind/LinkMind.jar
COPY --chown=linkmind:linkmind lagi-web/src/main/resources/lagi.yml /opt/linkmind/config/lagi.yml

USER linkmind

VOLUME ["/opt/linkmind/config", "/var/lib/linkmind/data"]
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS:-} -jar /opt/linkmind/LinkMind.jar \"$@\"", "--"]
