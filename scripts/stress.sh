#!/usr/bin/env bash
set -uo pipefail

CONCURRENCY=10
WAVES=3
URL="http://localhost:8080"
MIN_SIZE=1
MAX_SIZE=300

usage() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  --concurrency N    Parallel uploads per wave     (default: 10)"
    echo "  --waves N          Number of upload waves        (default: 3)"
    echo "  --url URL          Base URL of the app           (default: http://localhost:8080)"
    echo "  --min-size MB      Minimum file size in MB       (default: 1)"
    echo "  --max-size MB      Maximum file size in MB       (default: 300)"
    echo "  -h, --help         Show this help"
    echo ""
    echo "Example:"
    echo "  $0 --concurrency 20 --waves 5 --min-size 10 --max-size 500"
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --concurrency) CONCURRENCY="$2"; shift 2 ;;
        --waves)       WAVES="$2";       shift 2 ;;
        --url)         URL="$2";         shift 2 ;;
        --min-size)    MIN_SIZE="$2";    shift 2 ;;
        --max-size)    MAX_SIZE="$2";    shift 2 ;;
        -h|--help)     usage; exit 0 ;;
        *) echo "Unknown option: $1"; usage; exit 1 ;;
    esac
done

UPLOAD_ENDPOINT="${URL}/api/v1/files/upload"
TMP_DIR="/tmp/stress_upload_$$"

cleanup() {
    echo ""
    echo "Cleaning up temp files..."
    rm -rf "$TMP_DIR"
}
trap cleanup EXIT INT TERM

mkdir -p "$TMP_DIR"

rand_between() {
    local min=$1 max=$2
    echo $(( min + RANDOM % (max - min + 1) ))
}

upload_file() {
    local file="$1"
    local size_mb="$2"
    local result_file="$3"
    local filename
    filename=$(basename "$file")

    local response
    response=$(curl -s -o /dev/null \
        -w "%{http_code} %{time_total}" \
        -X POST \
        -F "document=@${file}" \
        "${UPLOAD_ENDPOINT}" 2>&1)

    local http_code time_total
    http_code=$(echo "$response" | awk '{print $1}')
    time_total=$(echo "$response" | awk '{print $2}')

    local status="FAIL"
    if [[ "$http_code" == "200" ]]; then
        status="OK"
    fi

    echo "$status $filename $size_mb $time_total $http_code" > "$result_file"
}

total_ok=0
total_fail=0
total_mb=0
all_times=()

max_per_wave=$(( CONCURRENCY * MAX_SIZE ))

echo "==========================================================="
echo " UPLOAD STRESS TEST"
echo "==========================================================="
echo " Endpoint:     ${UPLOAD_ENDPOINT}"
echo " Waves:        ${WAVES}"
echo " Concurrency:  ${CONCURRENCY} uploads/wave"
echo " File sizes:   ${MIN_SIZE}MB – ${MAX_SIZE}MB (random per upload)"
echo " Max disk use: ~${max_per_wave}MB per wave (temp files)"
echo " Note:         files are generated from /dev/urandom"
echo "==========================================================="

for wave in $(seq 1 "$WAVES"); do
    echo ""
    echo "Wave ${wave}/${WAVES} — generating ${CONCURRENCY} random files..."

    WAVE_DIR="${TMP_DIR}/wave_${wave}"
    RESULTS_DIR="${TMP_DIR}/results_${wave}"
    mkdir -p "$WAVE_DIR" "$RESULTS_DIR"

    # Generate files and launch uploads concurrently
    pids=()
    for i in $(seq 1 "$CONCURRENCY"); do
        size=$(rand_between "$MIN_SIZE" "$MAX_SIZE")
        filename="file_${i}_${size}mb.bin"
        filepath="${WAVE_DIR}/${filename}"
        dd if=/dev/urandom of="$filepath" bs=1m count="$size" 2>/dev/null
        result_file="${RESULTS_DIR}/result_${i}"
        upload_file "$filepath" "$size" "$result_file" &
        pids+=($!)
    done

    echo "Wave ${wave}/${WAVES} — ${CONCURRENCY} uploads in flight..."
    echo "-----------------------------------------------------------"

    for pid in "${pids[@]}"; do
        wait "$pid" || true
    done

    # Aggregate wave results
    wave_ok=0
    wave_fail=0
    wave_mb=0
    wave_times=()

    for i in $(seq 1 "$CONCURRENCY"); do
        result_file="${RESULTS_DIR}/result_${i}"
        [[ -f "$result_file" ]] || continue

        read -r status filename size_mb time_total http_code < "$result_file"

        if [[ "$status" == "OK" ]]; then
            printf "  [OK]   %-38s %4sMB  %6ss  HTTP %s\n" "$filename" "$size_mb" "$time_total" "$http_code"
            wave_ok=$(( wave_ok + 1 ))
        else
            printf "  [FAIL] %-38s %4sMB  %6ss  HTTP %s\n" "$filename" "$size_mb" "$time_total" "$http_code"
            wave_fail=$(( wave_fail + 1 ))
        fi

        wave_mb=$(( wave_mb + size_mb ))
        wave_times+=("$time_total")
        all_times+=("$time_total")
    done

    wave_avg="0.00"
    if [[ ${#wave_times[@]} -gt 0 ]]; then
        wave_avg=$(printf '%s\n' "${wave_times[@]}" | awk '{sum+=$1} END {printf "%.2f", sum/NR}')
    fi

    echo "-----------------------------------------------------------"
    printf "  Results: %d/%d OK | avg %ss | %dMB uploaded\n" \
        "$wave_ok" "$CONCURRENCY" "$wave_avg" "$wave_mb"

    total_ok=$(( total_ok + wave_ok ))
    total_fail=$(( total_fail + wave_fail ))
    total_mb=$(( total_mb + wave_mb ))
done

overall_avg="0.00"
if [[ ${#all_times[@]} -gt 0 ]]; then
    overall_avg=$(printf '%s\n' "${all_times[@]}" | awk '{sum+=$1} END {printf "%.2f", sum/NR}')
fi

total=$(( total_ok + total_fail ))

echo ""
echo "==========================================================="
echo " STRESS TEST COMPLETE"
echo "==========================================================="
printf " TOTAL:  %d/%d OK | %d failed\n" "$total_ok" "$total" "$total_fail"
printf " TIMING: avg %ss per upload\n" "$overall_avg"
printf " DATA:   %dMB total uploaded\n" "$total_mb"
echo "==========================================================="
