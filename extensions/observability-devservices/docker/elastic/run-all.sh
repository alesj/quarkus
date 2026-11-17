#!/usr/bin/env bash
set -euo pipefail

# All-in-one startup script for Elasticsearch + Kibana + APM Server.
# Modeled after grafana/docker-otel-lgtm's run-all.sh.
#
# Startup order matters:
#   1. Elasticsearch — must be healthy before creating tokens
#   2. Create Kibana service token (elastic superuser is forbidden in Kibana 9+)
#   3. Kibana + APM Server — can start in parallel once ES is ready

READY_FILE="${READY_FILE:-/tmp/ready}"
SHUTDOWN_TIMEOUT="${ELASTIC_SHUTDOWN_TIMEOUT_SECONDS:-10}"
LOG_DIR="${ELASTIC_LOG_DIR:-/opt/elastic/logs}"

mkdir -p "$LOG_DIR"

declare -A component_pids
declare -A component_ready
declare -A component_start

start_component() {
    local name="$1"
    component_ready[$name]=false
    component_start[$name]=$(date +%s)
    echo "Starting $name ..."
}

# ── Signal handling ──────────────────────────────────────────────────
shutdown() {
    trap '' SIGTERM SIGINT
    echo ""
    echo "Shutting down ..."
    stop_components
    exit 0
}

stop_components() {
    local pids
    pids=$(jobs -pr)
    if [[ -n "$pids" ]]; then
        kill $pids 2>/dev/null || true
        (
            sleep "$SHUTDOWN_TIMEOUT"
            kill -9 $pids 2>/dev/null || true
        ) &
        local watchdog=$!
        for pid in $pids; do
            wait "$pid" 2>/dev/null || true
        done
        kill "$watchdog" 2>/dev/null || true
        wait "$watchdog" 2>/dev/null || true
    fi
}

trap shutdown SIGTERM SIGINT

# ── Phase 0: Bootstrap elastic password into keystore ────────────────
ES_HOME=/opt/elastic/elasticsearch
if [[ ! -f "$ES_HOME/config/elasticsearch.keystore" ]]; then
    "$ES_HOME/bin/elasticsearch-keystore" create
fi
if ! ("$ES_HOME/bin/elasticsearch-keystore" list | grep -q '^bootstrap.password$'); then
    echo "$ELASTIC_PASSWORD" | "$ES_HOME/bin/elasticsearch-keystore" add -x 'bootstrap.password'
fi

# ── Phase 1: Start Elasticsearch ─────────────────────────────────────
start_component "elasticsearch"
/opt/elastic/elasticsearch/bin/elasticsearch \
    -Expack.license.self_generated.type=trial \
    -Expack.security.enabled=true \
    -Expack.security.authc.api_key.enabled=true \
    -Expack.security.transport.ssl.enabled=false \
    -Expack.security.http.ssl.enabled=false \
    -Ediscovery.type=single-node \
    -Enetwork.host=0.0.0.0 \
    -Ecluster.routing.allocation.disk.threshold_enabled=false \
    ${ELASTICSEARCH_EXTRA_ARGS:-} \
    >> "$LOG_DIR/elasticsearch.log" 2>&1 &
component_pids[elasticsearch]=$!

echo ""
echo "Waiting for Elasticsearch to become ready ..."

while true; do
    running_pids=$(jobs -pr)
    pid="${component_pids[elasticsearch]}"
    if ! echo "$running_pids" | grep -qw "$pid"; then
        echo "ERROR: elasticsearch (PID $pid) exited before becoming ready."
        exit 1
    fi
    if curl -sf -u "elastic:${ELASTIC_PASSWORD}" "http://127.0.0.1:9200/_cluster/health" -o /dev/null 2>/dev/null; then
        elapsed=$(( $(date +%s) - ${component_start[elasticsearch]} ))
        echo "elasticsearch is ready. (${elapsed}s)"
        component_ready[elasticsearch]=true
        break
    fi
    sleep 1
done

# ── Phase 2: Create Kibana service account token ─────────────────────
echo "Creating Kibana service account token ..."

KIBANA_TOKEN=$(curl -sf -u "elastic:${ELASTIC_PASSWORD}" \
    -X POST "http://127.0.0.1:9200/_security/service/elastic/kibana/credential/token/devservice" \
    -H 'Content-Type: application/json' | \
    grep -o '"value":"[^"]*"' | sed 's/"value":"//;s/"//')

if [[ -z "$KIBANA_TOKEN" ]]; then
    echo "ERROR: Failed to create Kibana service account token."
    stop_components
    exit 1
fi
echo "Kibana service token created."

# ── Phase 3: Start Kibana and APM Server ─────────────────────────────
start_component "kibana"
/opt/elastic/kibana/bin/kibana \
    --server.host=0.0.0.0 \
    --elasticsearch.hosts=http://127.0.0.1:9200 \
    --elasticsearch.serviceAccountToken="$KIBANA_TOKEN" \
    --xpack.fleet.enabled=false \
    --xpack.reporting.enabled="${KIBANA_REPORTING_ENABLED:-false}" \
    --xpack.canvas.enabled="${KIBANA_CANVAS_ENABLED:-false}" \
    --xpack.securitySolution.enabled="${KIBANA_SECURITY_SOLUTION_ENABLED:-false}" \
    --xpack.cases.enabled="${KIBANA_CASES_ENABLED:-false}" \
    ${KIBANA_EXTRA_ARGS:-} \
    >> "$LOG_DIR/kibana.log" 2>&1 &
component_pids[kibana]=$!

start_component "apm-server"
/opt/elastic/apm-server/apm-server \
    -e \
    --path.home /opt/elastic/apm-server \
    --path.config /opt/elastic/apm-server \
    --path.data /opt/elastic/apm-server/data \
    --path.logs /opt/elastic/apm-server/logs \
    ${APM_SERVER_EXTRA_ARGS:-} \
    >> "$LOG_DIR/apm-server.log" 2>&1 &
component_pids[apm-server]=$!

# ── Phase 4: Wait for remaining components ───────────────────────────
declare -A health_urls
health_urls[kibana]="http://127.0.0.1:5601/api/status"
health_urls[apm-server]="http://127.0.0.1:8200/"

check_health() {
    local name="$1"
    local url="${health_urls[$name]}"
    if curl -sf "$url" -o /dev/null 2>/dev/null; then
        return 0
    fi
    return 1
}

all_ready() {
    for name in "${!component_ready[@]}"; do
        if [[ "${component_ready[$name]}" != "true" ]]; then
            return 1
        fi
    done
    return 0
}

echo ""
echo "Waiting for Kibana and APM Server to become ready ..."

while ! all_ready; do
    running_pids=$(jobs -pr)
    for name in kibana apm-server; do
        if [[ "${component_ready[$name]}" == "true" ]]; then
            continue
        fi
        pid="${component_pids[$name]}"
        if ! echo "$running_pids" | grep -qw "$pid"; then
            echo "ERROR: $name (PID $pid) exited before becoming ready."
            stop_components
            exit 1
        fi
    done

    for name in kibana apm-server; do
        if [[ "${component_ready[$name]}" == "true" ]]; then
            continue
        fi
        if check_health "$name"; then
            elapsed=$(( $(date +%s) - ${component_start[$name]} ))
            echo "$name is ready. (${elapsed}s)"
            component_ready[$name]=true
        fi
    done

    sleep 1
done

touch "$READY_FILE"

echo ""
echo "=============================================="
echo "All components are up and running."
echo "=============================================="
echo ""
echo "Endpoints:"
echo "  Elasticsearch : http://localhost:9200"
echo "  Kibana        : http://localhost:5601"
echo "  APM Server    : http://localhost:8200"
echo "  OTLP gRPC     : localhost:8200 (via APM Server)"
echo "  OTLP HTTP     : http://localhost:8200"
echo ""
echo "Kibana MCP      : http://localhost:5601/api/agent_builder/mcp"
echo ""
echo "Credentials     : elastic / ${ELASTIC_PASSWORD}"
echo ""
echo "Logs            : $LOG_DIR/"
echo "=============================================="
echo ""

# Block until signaled
sleep infinity &
wait $!
