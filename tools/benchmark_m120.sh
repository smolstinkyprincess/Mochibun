#!/usr/bin/env bash
# Repeatable Mochibun performance snapshot for the ALT Mive Style Folder AT-M120.
#
# 1. Always: APK size + classes*.dex sizes for every APK under app/build/outputs/apk/.
# 2. When an adb device is connected and Mochibun is installed:
#      - median `am start -W` launch time over $RUNS runs (force-stop between runs)
#      - Home idle frame stats (gfxinfo reset -> 5 s idle -> dump)
#      - App Drawer fling frame stats (swipe up to open, fling the list, dump)
#      - memory footprint (dumpsys meminfo TOTAL PSS / TOTAL RSS)
#
# Usage (from the repo root):
#   tools/benchmark_m120.sh                 # sizes + device run if one is connected
#   RUNS=7 tools/benchmark_m120.sh          # more launch samples
#   ANDROID_SERIAL=<serial> tools/benchmark_m120.sh   # pick a device when several are attached
#   NO_DEVICE=1 tools/benchmark_m120.sh     # APK/dex sizes only
#
# Notes:
#   - Mochibun is the HOME app and usually the bound IME, so after `am force-stop` the system
#     rebinds the IME/home and `am start -W` typically reports LaunchState WARM. That matches the
#     methodology behind the numbers in AGENTS.md; the LaunchState of every run is shown.
#   - The drawer is closed with KEYCODE_HOME (not BACK): with Soft-Key Nav on, BACK in the drawer
#     cycles sort mode instead of closing it.
#   - Run on a quiet device (screen on, unlocked, lid open, charger state as you want to measure).
set -euo pipefail

PKG=one.crinklecod.mochibun
ACTIVITY="$PKG/.ui.MainActivity"
RUNS=${RUNS:-5}
APK_ROOT=app/build/outputs/apk
IDLE_SECONDS=5
FLINGS=6

cd "$(dirname "$0")/.."

# ---------------------------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------------------------
fmt_mib() { awk -v b="$1" 'BEGIN { printf "%.2f MiB", b / 1048576 }'; }
fmt_bytes() { printf "%'d B" "$1" 2>/dev/null || printf "%d B" "$1"; }
adb_sh() { { adb shell "$@" 2>/dev/null || true; } | tr -d '\r'; }
hr() { printf '%s\n' "------------------------------------------------------------------------"; }

median() {
    # Prints the median of the numbers given as arguments (mean of the middle two when even).
    printf '%s\n' "$@" | sort -n | awk '{ v[NR] = $1 } END {
        if (NR == 0) { print "n/a"; exit }
        if (NR % 2) print v[(NR + 1) / 2]; else printf "%.0f\n", (v[NR / 2] + v[NR / 2 + 1]) / 2
    }'
}

# Extracts the headline numbers from a `dumpsys gfxinfo <pkg>` dump read on stdin.
gfx_summary() {
    awk '
        function val(line) { sub(/^[^:]*:[ \t]*/, "", line); return line }
        /^[ \t]*Total frames rendered:/           { frames = val($0) }
        /^[ \t]*Janky frames:/ && !jank          { jank = val($0) }
        /^[ \t]*50th percentile:/                { p50 = val($0) }
        /^[ \t]*90th percentile:/                { p90 = val($0) }
        /^[ \t]*95th percentile:/                { p95 = val($0) }
        /^[ \t]*99th percentile:/                { p99 = val($0) }
        /^[ \t]*Number Missed Vsync:/            { vsync = val($0) }
        /^[ \t]*Number Slow UI thread:/          { slowui = val($0) }
        /^[ \t]*Number Slow issue draw commands:/ { slowdraw = val($0) }
        /^[ \t]*50th gpu percentile:/            { g50 = val($0) }
        /^[ \t]*95th gpu percentile:/            { g95 = val($0) }
        /^[ \t]*99th gpu percentile:/            { g99 = val($0) }
        END {
            if (frames == "") { print "    (no frame data: is the app in the foreground?)"; exit }
            printf "    frames %s | janky %s\n", frames, jank
            printf "    CPU p50 %s / p90 %s / p95 %s / p99 %s\n", p50, p90, p95, p99
            if (g50 != "") printf "    GPU p50 %s / p95 %s / p99 %s\n", g50, g95, g99
            printf "    missed vsync %s | slow UI thread %s | slow draw cmds %s\n", vsync, slowui, slowdraw
        }'
}

# ---------------------------------------------------------------------------------------------
# 1. APK + dex sizes
# ---------------------------------------------------------------------------------------------
APK_REPORT=""
shopt -s nullglob
apks=("$APK_ROOT"/*/*.apk)
shopt -u nullglob

if [ ${#apks[@]} -eq 0 ]; then
    APK_REPORT="  No APKs under $APK_ROOT (run ./gradlew app:assembleRelease or app:assembleDebug)."
else
    for apk in "${apks[@]}"; do
        size=$(stat -c %s "$apk")
        APK_REPORT+="  $apk"$'\n'
        APK_REPORT+="    APK size: $(fmt_bytes "$size") ($(fmt_mib "$size"))"$'\n'
        if command -v unzip >/dev/null 2>&1; then
            # unzip -l columns: uncompressed length, date, time, name
            # classes.dex is dex #1; sort numerically so classes2..classesN follow it in order.
            dex_lines=$(unzip -l "$apk" | awk '$4 ~ /^classes[0-9]*\.dex$/ {
                k = $4; gsub(/[^0-9]/, "", k); print (k == "" ? 1 : k), $4, $1
            }' | sort -n | awk '{ print $2, $3 }')
            dex_total=0
            while read -r name bytes; do
                [ -n "${name:-}" ] || continue
                APK_REPORT+="    $(printf '%-14s' "$name") $(fmt_bytes "$bytes") ($(fmt_mib "$bytes"))"$'\n'
                dex_total=$((dex_total + bytes))
            done <<< "$dex_lines"
            APK_REPORT+="    $(printf '%-14s' "dex total") $(fmt_bytes "$dex_total") ($(fmt_mib "$dex_total"))"$'\n'
        else
            APK_REPORT+="    (install 'unzip' to list classes*.dex sizes)"$'\n'
        fi
    done
fi

# ---------------------------------------------------------------------------------------------
# 2. On-device measurements
# ---------------------------------------------------------------------------------------------
DEVICE_REPORT=""
device_ready() {
    [ -z "${NO_DEVICE:-}" ] || return 1
    command -v adb >/dev/null 2>&1 || return 1
    [ "$(adb get-state 2>/dev/null)" = "device" ] || return 1
    adb_sh pm path "$PKG" | grep -q '^package:'
}

if device_ready; then
    model=$(adb_sh getprop ro.product.model)
    sdk=$(adb_sh getprop ro.build.version.sdk)
    version=$(adb_sh dumpsys package "$PKG" | awk -F= '/versionName=/ { print $2; exit }')
    echo "Device: $model (API $sdk), $PKG $version" >&2

    # --- Launch time ---------------------------------------------------------------------
    echo "Measuring launch time ($RUNS runs)..." >&2
    totals=()
    states=()
    for i in $(seq 1 "$RUNS"); do
        adb_sh am force-stop "$PKG" >/dev/null
        sleep 2
        out=$(adb_sh am start -W -n "$ACTIVITY")
        t=$(awk -F': *' '/^TotalTime:/ { print $2 }' <<< "$out")
        s=$(awk -F': *' '/^LaunchState:/ { print $2 }' <<< "$out")
        [ -n "$t" ] || t=$(awk -F': *' '/^WaitTime:/ { print $2 }' <<< "$out")
        if [ -n "$t" ]; then
            totals+=("$t")
            states+=("${s:-?}")
            echo "  run $i: ${t} ms (${s:-?})" >&2
        else
            echo "  run $i: no timing reported" >&2
        fi
        sleep 3
    done
    if [ ${#totals[@]} -gt 0 ]; then
        launch_median=$(median "${totals[@]}")
        launch_min=$(printf '%s\n' "${totals[@]}" | sort -n | head -1)
        launch_max=$(printf '%s\n' "${totals[@]}" | sort -n | tail -1)
        launch_states=$(printf '%s\n' "${states[@]}" | sort | uniq -c | awk '{ printf "%s%s x%s", sep, $2, $1; sep = ", " }')
        DEVICE_REPORT+="  Launch (am start -W, ${#totals[@]} runs): median ${launch_median} ms (min ${launch_min}, max ${launch_max}) [${launch_states}]"$'\n'
        DEVICE_REPORT+="    samples: ${totals[*]}"$'\n'
    else
        DEVICE_REPORT+="  Launch: no TotalTime/WaitTime reported by am start -W"$'\n'
    fi

    # --- Home idle frames ----------------------------------------------------------------
    echo "Measuring Home idle frames (${IDLE_SECONDS} s)..." >&2
    sleep 3 # let post-launch work settle on Home
    adb_sh dumpsys gfxinfo "$PKG" reset >/dev/null
    sleep "$IDLE_SECONDS"
    DEVICE_REPORT+="  Home idle (${IDLE_SECONDS} s):"$'\n'
    DEVICE_REPORT+="$(adb_sh dumpsys gfxinfo "$PKG" | gfx_summary)"$'\n'

    # --- App Drawer fling frames ---------------------------------------------------------
    echo "Measuring App Drawer flings..." >&2
    wm=$(adb_sh wm size | awk -F': *' '/Override size/ { o = $2 } /Physical size/ { p = $2 } END { print (o != "" ? o : p) }')
    width=${wm%x*}
    height=${wm#*x}
    if ! [[ "$width" =~ ^[0-9]+$ && "$height" =~ ^[0-9]+$ ]]; then
        width=480
        height=800
    fi
    x=$((width / 2))
    # Swipe up on Home opens the drawer.
    adb_sh input swipe "$x" $((height * 85 / 100)) "$x" $((height * 35 / 100)) 200 >/dev/null
    sleep 2
    adb_sh dumpsys gfxinfo "$PKG" reset >/dev/null
    for i in $(seq 1 "$FLINGS"); do
        if [ $((i % 3)) -eq 0 ]; then
            # Every third fling goes back up so the list keeps content to scroll through.
            adb_sh input swipe "$x" $((height * 30 / 100)) "$x" $((height * 80 / 100)) 80 >/dev/null
        else
            adb_sh input swipe "$x" $((height * 80 / 100)) "$x" $((height * 30 / 100)) 80 >/dev/null
        fi
        sleep 0.6
    done
    sleep 1
    DEVICE_REPORT+="  App Drawer flings (${FLINGS} flings):"$'\n'
    DEVICE_REPORT+="$(adb_sh dumpsys gfxinfo "$PKG" | gfx_summary)"$'\n'
    # Back to Home (HOME closes the drawer regardless of Soft-Key Nav).
    adb_sh input keyevent KEYCODE_HOME >/dev/null
    sleep 3

    # --- Memory --------------------------------------------------------------------------
    echo "Measuring memory..." >&2
    meminfo=$(adb_sh dumpsys meminfo "$PKG")
    pss=$(grep -o 'TOTAL PSS: *[0-9]*' <<< "$meminfo" | head -1 | grep -o '[0-9]*$' || true)
    rss=$(grep -o 'TOTAL RSS: *[0-9]*' <<< "$meminfo" | head -1 | grep -o '[0-9]*$' || true)
    if [ -z "$pss" ]; then
        # Older dumpsys formats: first column of the "TOTAL" row is PSS.
        pss=$(awk '$1 == "TOTAL" && $2 ~ /^[0-9]+$/ { print $2; exit }' <<< "$meminfo")
    fi
    summary_row() { awk -v k="$1" 'index($0, k) { sub(/^[^:]*:[ \t]*/, ""); print $1; exit }' <<< "$meminfo"; }
    java_heap=$(summary_row "Java Heap:")
    native_heap=$(summary_row "Native Heap:")
    graphics=$(summary_row "Graphics:")
    kb_to_mib() { [ -n "$1" ] && awk -v k="$1" 'BEGIN { printf "%.1f MiB", k / 1024 }' || echo "n/a"; }
    DEVICE_REPORT+="  Memory (Home, after drawer):"$'\n'
    DEVICE_REPORT+="    TOTAL PSS ${pss:-n/a} KB ($(kb_to_mib "${pss:-}")) | TOTAL RSS ${rss:-n/a} KB ($(kb_to_mib "${rss:-}"))"$'\n'
    DEVICE_REPORT+="    Java Heap $(kb_to_mib "${java_heap:-}") | Native Heap $(kb_to_mib "${native_heap:-}") | Graphics $(kb_to_mib "${graphics:-}")"$'\n'
else
    if [ -n "${NO_DEVICE:-}" ]; then
        DEVICE_REPORT="  Skipped (NO_DEVICE set)."
    elif ! command -v adb >/dev/null 2>&1; then
        DEVICE_REPORT="  Skipped (adb not found in PATH)."
    elif [ "$(adb get-state 2>/dev/null)" != "device" ]; then
        DEVICE_REPORT="  Skipped (no adb device connected; set ANDROID_SERIAL if several are attached)."
    else
        DEVICE_REPORT="  Skipped ($PKG is not installed on the device)."
    fi
fi

# ---------------------------------------------------------------------------------------------
# Report
# ---------------------------------------------------------------------------------------------
echo
hr
echo " Mochibun AT-M120 benchmark  ($(date '+%Y-%m-%d %H:%M'), git $(git rev-parse --short HEAD 2>/dev/null || echo '?')$(git diff --quiet 2>/dev/null || echo '+dirty'))"
hr
echo " APK & dex sizes"
printf '%s\n' "${APK_REPORT%$'\n'}"
hr
echo " Device"
printf '%s\n' "${DEVICE_REPORT%$'\n'}"
hr
