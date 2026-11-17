# All-in-one Elastic observability image for Quarkus Dev Services.
# Bundles Elasticsearch, Kibana, and APM Server in a single container.
# Modeled after grafana/docker-otel-lgtm.
#
# APM Server natively accepts OTLP on port 8200 (both gRPC and HTTP).
# Kibana exposes the Agent Builder MCP endpoint at /api/agent_builder/mcp.

# renovate: datasource=docker depName=docker.elastic.co/elasticsearch/elasticsearch
ARG ELASTICSEARCH_VERSION=9.5.6
# renovate: datasource=docker depName=docker.elastic.co/kibana/kibana
ARG KIBANA_VERSION=9.5.6
# renovate: datasource=docker depName=docker.elastic.co/apm/apm-server
ARG APM_SERVER_VERSION=9.5.6

# ── Stage 1: Extract binaries from official images ───────────────────

FROM docker.elastic.co/elasticsearch/elasticsearch:${ELASTICSEARCH_VERSION} AS elasticsearch-source

FROM docker.elastic.co/kibana/kibana:${KIBANA_VERSION} AS kibana-source

FROM docker.elastic.co/apm/apm-server:${APM_SERVER_VERSION} AS apm-server-source

# ── Stage 2: Build the final image ──────────────────────────────────

FROM docker.io/redhat/ubi9-minimal:9.5

ARG ELASTICSEARCH_VERSION
ARG KIBANA_VERSION
ARG APM_SERVER_VERSION

# Install minimal runtime dependencies (curl-minimal is already in ubi9-minimal)
RUN microdnf install -y --nodocs \
        shadow-utils \
        findutils \
        procps-ng \
    && microdnf clean all

# Create a shared user for all processes
RUN groupadd -g 1000 elastic && \
    useradd -u 1000 -g 1000 -d /opt/elastic elastic

# ── Elasticsearch ────────────────────────────────────────────────────

COPY --from=elasticsearch-source /usr/share/elasticsearch /opt/elastic/elasticsearch

# Elasticsearch needs a writable data/logs/config dir owned by elastic
RUN mkdir -p /opt/elastic/elasticsearch/data \
             /opt/elastic/elasticsearch/logs \
    && chown -R elastic:elastic /opt/elastic/elasticsearch

# Minimal elasticsearch.yml — most config is passed via -E flags in run-all.sh
RUN echo 'path.data: /opt/elastic/elasticsearch/data' > /opt/elastic/elasticsearch/config/elasticsearch.yml && \
    echo 'path.logs: /opt/elastic/elasticsearch/logs' >> /opt/elastic/elasticsearch/config/elasticsearch.yml

# ── Kibana ───────────────────────────────────────────────────────────

COPY --from=kibana-source /usr/share/kibana /opt/elastic/kibana
RUN chown -R elastic:elastic /opt/elastic/kibana

# ── APM Server ───────────────────────────────────────────────────────

COPY --from=apm-server-source /usr/share/apm-server /opt/elastic/apm-server
COPY apm-server.yml /opt/elastic/apm-server/apm-server.yml
RUN mkdir -p /opt/elastic/apm-server/data /opt/elastic/apm-server/logs && \
    chmod go-w /opt/elastic/apm-server/apm-server.yml && \
    chown -R elastic:elastic /opt/elastic/apm-server

# ── Scripts ──────────────────────────────────────────────────────────

RUN mkdir -p /opt/elastic/logs && chown elastic:elastic /opt/elastic/logs

COPY run-all.sh healthcheck.sh /opt/elastic/
RUN chmod +x /opt/elastic/run-all.sh /opt/elastic/healthcheck.sh

# ── Environment ──────────────────────────────────────────────────────

ENV ELASTIC_PASSWORD=changeme
ENV ES_JAVA_OPTS="-Xms512m -Xmx512m -XX:+AlwaysPreTouch"

ENV ELASTICSEARCH_VERSION=${ELASTICSEARCH_VERSION}
ENV KIBANA_VERSION=${KIBANA_VERSION}
ENV APM_SERVER_VERSION=${APM_SERVER_VERSION}

# Kibana plugin toggles — disabled by default to speed up startup
ENV KIBANA_REPORTING_ENABLED=false
ENV KIBANA_CANVAS_ENABLED=false
ENV KIBANA_SECURITY_SOLUTION_ENABLED=false
ENV KIBANA_CASES_ENABLED=false

# Support running as arbitrary UID (OpenShift)
# Exclude apm-server.yml from group-writable — APM Server enforces strict perms
RUN chgrp -R 0 /opt/elastic && chmod -R g=u /opt/elastic && \
    chmod go-w /opt/elastic/apm-server/apm-server.yml

# ── Ports ────────────────────────────────────────────────────────────
# 9200 - Elasticsearch HTTP
# 5601 - Kibana
# 8200 - APM Server (Elastic APM + OTLP gRPC + OTLP HTTP)

EXPOSE 9200 5601 8200

# ── Health check ─────────────────────────────────────────────────────

HEALTHCHECK --interval=30s --timeout=5s --retries=10 \
    CMD /opt/elastic/healthcheck.sh

# ── Run ──────────────────────────────────────────────────────────────

WORKDIR /opt/elastic
USER elastic

ENTRYPOINT ["/opt/elastic/run-all.sh"]
