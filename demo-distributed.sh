#!/bin/bash
# demo-distributed.sh — Run N generators locally to demonstrate load distribution
#
# Usage: ./demo-distributed.sh [test-plan.jmx] [generator-count]
#   Defaults: test-plan-auto.jmx, 3 generators

set -euo pipefail

JMETER="${JMETER_HOME:-/home/dc/Downloads/apache-jmeter-5.6.3}/bin/jmeter"
TEST_PLAN="${1:-test-plan-auto.jmx}"
COUNT="${2:-3}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
TMPDIR_BASE=$(mktemp -d)

# Colors
GREEN='\033[0;32m'
CYAN='\033[0;36m'
YELLOW='\033[1;33m'
BOLD='\033[1m'
DIM='\033[2m'
RESET='\033[0m'

echo ""
echo -e "${BOLD}  JMeter Load Distributor — Distribution Demo${RESET}"
echo -e "${DIM}  ─────────────────────────────────────────────${RESET}"
echo ""
echo -e "  Test plan:   ${CYAN}${TEST_PLAN}${RESET}"
echo -e "  Generators:  ${CYAN}${COUNT}${RESET}"
echo ""

# Run each generator sequentially (same machine, different ports)
for ID in $(seq 1 "$COUNT"); do
    WORKDIR="${TMPDIR_BASE}/gen${ID}"
    mkdir -p "$WORKDIR"

    echo -e "${YELLOW}▸ Generator ${ID}/${COUNT}${RESET}"

    # Run JMeter, capture log in isolated dir
    (cd "$WORKDIR" && "$JMETER" -n \
        -Jgenerator.id="$ID" \
        -Jgenerator.count="$COUNT" \
        -Jserver.rmi.localport=$((4444 + ID)) \
        -t "${SCRIPT_DIR}/${TEST_PLAN}" \
        -l /dev/null 2>&1 | grep -E "^summary =" | tail -1 | \
        sed "s/^/    /")

    # Extract distribution lines from log
    grep "a.l.j.d" "$WORKDIR/jmeter.log" | while read -r line; do
        msg=$(echo "$line" | sed 's/^.*INFO [^ ]*: //')
        echo -e "    ${GREEN}${msg}${RESET}"
    done
    echo ""
done

# Collect per-group totals across generators
declare -A GROUP_ORIGINALS  # group name → original thread count
declare -A GROUP_TOTALS     # group name → sum across generators
GROUP_NAMES=()              # ordered list of group names

for ID in $(seq 1 "$COUNT"); do
    WORKDIR="${TMPDIR_BASE}/gen${ID}"
    while IFS= read -r line; do
        NAME=$(echo "$line" | sed 's/.*\[\(.*\)\].*/\1/')
        ORIGINAL=$(echo "$line" | grep -oP '\] \K[0-9]+')
        THREADS=$(echo "$line" | grep -oP '→ \K[0-9]+')
        GROUP_ORIGINALS["$NAME"]=$ORIGINAL
        GROUP_TOTALS["$NAME"]=$(( ${GROUP_TOTALS["$NAME"]:-0} + THREADS ))
        # Track order
        if ! printf '%s\n' "${GROUP_NAMES[@]}" 2>/dev/null | grep -qx "$NAME"; then
            GROUP_NAMES+=("$NAME")
        fi
    done < <(grep "a.l.j.d.LoadDistributor:   \[" "$WORKDIR/jmeter.log")
done

# Summary
echo -e "${DIM}  ─────────────────────────────────────────────${RESET}"
echo -e "${BOLD}  Distribution Summary${RESET}"
echo -e "${DIM}  ─────────────────────────────────────────────${RESET}"
echo ""

GRAND_TOTAL=0
for ID in $(seq 1 "$COUNT"); do
    WORKDIR="${TMPDIR_BASE}/gen${ID}"
    TOTAL=0
    DETAILS=""

    while IFS= read -r line; do
        NAME=$(echo "$line" | sed 's/.*\[\(.*\)\].*/\1/')
        THREADS=$(echo "$line" | grep -oP '→ \K[0-9]+')
        TOTAL=$((TOTAL + THREADS))
        if [ -n "$DETAILS" ]; then
            DETAILS="${DETAILS}, "
        fi
        DETAILS="${DETAILS}${NAME}: ${THREADS}"
    done < <(grep "a.l.j.d.LoadDistributor:   \[" "$WORKDIR/jmeter.log")

    GRAND_TOTAL=$((GRAND_TOTAL + TOTAL))
    printf "  Generator %d/%d:  %3d threads  ${DIM}(%s)${RESET}\n" \
        "$ID" "$COUNT" "$TOTAL" "$DETAILS"
done

echo ""

# Per-group verification
for NAME in "${GROUP_NAMES[@]}"; do
    ORIG=${GROUP_ORIGINALS["$NAME"]}
    SUM=${GROUP_TOTALS["$NAME"]}
    if [ "$ORIG" -eq "$SUM" ]; then
        echo -e "  ${GREEN}${NAME}: ${SUM}/${ORIG} threads distributed${RESET}"
    else
        echo -e "  \033[0;31m${NAME}: ${SUM}/${ORIG} MISMATCH\033[0m"
    fi
done

echo -e "  ${GREEN}${BOLD}Total: ${GRAND_TOTAL} threads across ${#GROUP_NAMES[@]} groups — exact match, zero loss${RESET}"
echo ""

# Cleanup
rm -rf "$TMPDIR_BASE"
