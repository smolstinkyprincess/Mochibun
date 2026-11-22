#!/usr/bin/env bash
# Repeatable Mochibun performance snapshot for the ALT Mive Style Folder AT-M120.
#
# 1. Always: APK size + classes*.dex sizes for every APK under app/build/outputs/apk/.
# 2. When an adb device is connected and Mochibun is installed:
#      - median launch time over $RUNS runs: force-stop, then the system's HOME relaunch timed
#        from logcat "ActivityTaskManager: Displayed .../.ui.MainActivity: +NNNms"
#      - Home idle frame stats (gfxinfo reset -> 5 s idle -> dump)
#      - App Drawer fling frame stats (swipe up to open, fling the list, dump)
#      - memory footprint (dumpsys meminfo TOTAL PSS / TOTAL RSS)
#
# Usage (from the repo root):
#   tools/benchmark_m120.sh                 # sizes + device run if one is connected
#   RUNS=7 tools/benchmark_m120.sh          # more launch samples
#   ANDROID_SERIAL=<serial> tools/benchmark_m120.sh   # pick a device when several are attached
#   NO_DEVICE=1 tools/benchmark_m120.sh     # APK/dex sizes only
#   tools/benchmark_m120.sh --stress-settings          # Settings Hub stress only (see below)
#   STRESS_ROUNDS=5 tools/benchmark_m120.sh --stress-settings
#
# --stress-settings (replaces the launch / idle / drawer runs):
#   force-stop + launch -> one unmeasured warm-up round (lets the JIT compile the settings
#   screens; there is no dex2oat on the AT-M120) -> two measured phases, each preceded by a
#   gfxinfo reset and followed by a gfxinfo dump (plus a ${IDLE_SECONDS} s idle sample on the Hub):
#     A. Sub-screen navigation: jump keys 1-8 open every Settings Hub destination, BACK returns.
#     B. D-pad bursts + toggles: 16x DOWN / UP bursts on the Hub and 12x in every sub-screen,
#        plus switch / stepper flips (Keyboard "Hold for digit" toggle, Sound profile
#        stepper, Appearance > Widgets & Cards "System Monitor" toggle). Every setting is flipped
#        an EVEN number of times so it ends where it started.
#   then dumps meminfo. The Hub is opened with a short `#` tap on Home (Launcher Menu).
#   Fail-safe: before every key batch the Settings dialog window must still be focused, and every
#   screen change / setting flip is verified against the on-screen title (`uiautomator dump`,
#   ~3 s each, so a run takes several minutes). Any mismatch aborts without sending more keys.
#   Bursts use one `input keyevent K K K ...` call (one JVM spawn), so keys arrive faster than
#   hardware key repeat (~worst case); `input` waits for each event to be handled.
#
# Notes:
#   - Mochibun is the HOME app and usually the bound IME, so after `am force-stop` the system
#     relaunches it at once and `am start -W` would hit that instance (TotalTime 0). Launch time
#     is read from the relaunch's logcat Displayed line instead; if nothing relaunches it within
#     ~6 s (not the default HOME), the script starts it with `am start`.
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
STRESS_ROUNDS=${STRESS_ROUNDS:-3}
STRESS_SETTINGS=""
for arg in "$@"; do
    case "$arg" in
        --stress-settings) STRESS_SETTINGS=1 ;;
        -h|--help) sed -n '2,40p' "$0"; exit 0 ;;
        *) echo "Unknown argument: $arg" >&2; exit 2 ;;
    esac
done

cd "$(dirname "$0")/.."

# ---------------------------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------------------------
fmt_mib() { awk -v b="$1" 'BEGIN { printf "%.2f MiB", b / 1048576 }'; }
fmt_bytes() { printf "%'d B" "$1" 2>/dev/null || printf "%d B" "$1"; }
adb_sh() { { adb shell "$@" 2>/dev/null || true; } | tr -d '\r'; }
hr() { printf '%s\n' "------------------------------------------------------------------------"; }

# Reads logcat on stdin; prints the first "Displayed $ACTIVITY: +1s234ms" time in ms.
displayed_ms() {
    awk -v act="Displayed $ACTIVITY: +" '
        !found && index($0, act) {
            v = substr($0, index($0, act) + length(act))
            sub(/ .*/, "", v)          # drop " (total ...)" if present
            sub(/ms$/, "", v)
            ms = v
            if (index(v, "s")) { split(v, p, "s"); ms = p[1] * 1000 + p[2] }
            print ms + 0
            found = 1
        }'
}

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

# Appends a `dumpsys meminfo` summary (TOTAL PSS / RSS, Java / Native heap, Graphics) to
# DEVICE_REPORT under the given label.
mem_report() {
    echo "Measuring memory..." >&2
    local meminfo pss rss java_heap native_heap graphics
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
    DEVICE_REPORT+="  Memory ($1):"$'\n'
    DEVICE_REPORT+="    TOTAL PSS ${pss:-n/a} KB ($(kb_to_mib "${pss:-}")) | TOTAL RSS ${rss:-n/a} KB ($(kb_to_mib "${rss:-}"))"$'\n'
    DEVICE_REPORT+="    Java Heap $(kb_to_mib "${java_heap:-}") | Native Heap $(kb_to_mib "${native_heap:-}") | Graphics $(kb_to_mib "${graphics:-}")"$'\n'
}

# --- Settings Hub stress (--stress-settings) -------------------------------------------------
# Fail-safe by design: injected keys go wherever focus is, so a single missed jump key used to
# turn every later BACK / digit / CENTER into Home and App Drawer input. Every key batch is
# preceded by a cheap focus check (the Settings dialog window must still be focused) and every
# screen change is verified by reading the on-screen title via `uiautomator dump`. The first
# mismatch aborts the whole run (STRESS_ABORTED) instead of continuing blind.
STRESS_ABORTED=""
MAIN_WINDOW=""   # "Window{<id> ...}" of MainActivity, captured before the Hub opens

# Reads the whole dump (no early `exit`): under pipefail an early-closing reader turns the
# writer's SIGPIPE into a failed pipeline, which `set -e` treats as fatal.
focused_window() { adb_sh dumpsys window | awk -F'mCurrentFocus=' '/mCurrentFocus=/ && !done { print $2; done = 1 }' || true; }

stress_abort() {
    STRESS_ABORTED="$1"
    echo "  ABORTED: $1" >&2
    return 1
}

# The Settings Hub is a dialog window of MainActivity: same title, different window id.
in_settings_dialog() {
    local w
    w=$(focused_window)
    [[ "$w" == *"$PKG"* ]] || { stress_abort "focus left Mochibun ($w)"; return 1; }
    [ "$w" != "$MAIN_WINDOW" ] || { stress_abort "Settings dialog closed (focus is back on MainActivity)"; return 1; }
}

# Prints the SettingsScaffold title ("— ♡ <title> ♡ —") currently on screen, or nothing.
screen_title() {
    adb_sh uiautomator dump /data/local/tmp/mochibun_ui.xml >/dev/null
    adb_sh cat /data/local/tmp/mochibun_ui.xml | grep -o 'text="— ♡ [^"]* ♡ —"' \
        | sed -n -e '1{s/^text="— ♡ //; s/ ♡ —"$//; s/&amp;/\&/g; p}' || true
}

expect_screen() {
    local want=$1 got
    got=$(screen_title)
    [ "$got" = "$want" ] || { stress_abort "expected screen '$want', found '${got:-?}'"; return 1; }
}

# Guarded key injection. One `input` invocation per call: several keycodes are injected back to
# back in a single JVM (`input` waits for each event to be handled).
keys() {
    [ -z "$STRESS_ABORTED" ] || return 1
    in_settings_dialog || return 1
    adb_sh input keyevent "$@" >/dev/null
}
# Repeats a keycode N times inside one guarded `input keyevent` call.
burst() { local k=$1 n=$2 args=(); for _ in $(seq 1 "$n"); do args+=("$k"); done; keys "${args[@]}"; }

# Hub jump keys -> destination screen titles.
STRESS_DESTS=(1 2 3 4 5 6 7 8)
dest_title() {
    case $1 in
        1) echo "Appearance" ;; 2) echo "Keyboard" ;; 3) echo "Virtual Cursor" ;;
        4) echo "Sound & Feedback" ;; 5) echo "Behavior" ;; 6) echo "Permissions & Root" ;;
        7) echo "Backup & Restore" ;; 8) echo "About Mochibun" ;;
    esac
}

# Opens destination $1 from the Hub and verifies it; returns non-zero (and aborts) otherwise.
open_dest() {
    keys "KEYCODE_$1" || return 1
    sleep 1
    expect_screen "$(dest_title "$1")"
}

# BACK from a verified sub-screen, then verify we are on the Hub again.
back_to_hub() {
    keys KEYCODE_BACK || return 1
    sleep 0.8
    expect_screen "Settings Hub"
}

stress_phase_navigation() {
    for d in "${STRESS_DESTS[@]}"; do
        open_dest "$d" || return 1
        sleep 0.2
        back_to_hub || return 1
    done
}

stress_phase_bursts() {
    burst KEYCODE_DPAD_DOWN 16 || return 1
    burst KEYCODE_DPAD_UP 16 || return 1
    expect_screen "Settings Hub" || return 1
    for d in "${STRESS_DESTS[@]}"; do
        open_dest "$d" || return 1
        burst KEYCODE_DPAD_DOWN 12 || return 1
        burst KEYCODE_DPAD_UP 12 || return 1
        # Flips run only on a re-verified screen, always an even number of times.
        case $d in
            1)  # Appearance: `6` opens Widgets & Cards; `1` jumps to + flips "System Monitor"
                # twice (even -> restored), then BACK returns to Appearance.
                expect_screen "Appearance" || return 1
                keys KEYCODE_6 || return 1
                sleep 0.5
                expect_screen "Widgets & Cards" || return 1
                keys KEYCODE_1 || return 1
                sleep 0.3
                keys KEYCODE_1 || return 1
                sleep 0.3
                keys KEYCODE_BACK || return 1
                sleep 0.5 ;;
            2)  # Keyboard: `3` jumps to + flips "Hold for digit"; RIGHT x8 flips it 8 more times;
                # `3` again -> 10 flips total.
                expect_screen "Keyboard" || return 1
                keys KEYCODE_3 || return 1
                burst KEYCODE_DPAD_RIGHT 8 || return 1
                keys KEYCODE_3 || return 1 ;;
            4)  # Sound & Feedback: `4` jumps to row 3 = Sound Profile stepper (cyclic) and
                # steps it +1; RIGHT/LEFT pairs, then one LEFT -> net zero. (A jump key, not DOWN x3:
                # the DOWN burst can park focus on the floating Back pill, which swallows LEFT/RIGHT.)
                expect_screen "Sound & Feedback" || return 1
                keys KEYCODE_4 || return 1
                for _ in 1 2 3 4; do keys KEYCODE_DPAD_RIGHT KEYCODE_DPAD_LEFT || return 1; done
                keys KEYCODE_DPAD_LEFT || return 1 ;;
        esac
        sleep 0.3
        expect_screen "$(dest_title "$d")" || return 1
        back_to_hub || return 1
    done
}

run_settings_stress() {
    echo "Settings stress: relaunching $PKG..." >&2
    adb_sh am force-stop "$PKG" >/dev/null
    sleep 2
    adb_sh am start -W -n "$ACTIVITY" >/dev/null
    sleep 4
    MAIN_WINDOW=$(focused_window)
    if [[ "$MAIN_WINDOW" != *"$PKG/$PKG.ui.MainActivity"* ]]; then
        DEVICE_REPORT+="  Settings stress: not started (MainActivity is not focused: ${MAIN_WINDOW:-?})"$'\n'
        return
    fi
    # Short `#` tap on Home opens the Launcher Menu (Settings Hub). Unguarded: the dialog is not
    # open yet. Verified right after.
    adb_sh input keyevent KEYCODE_POUND >/dev/null
    sleep 2
    if ! { in_settings_dialog && expect_screen "Settings Hub"; }; then
        DEVICE_REPORT+="  Settings stress: ABORTED before start (${STRESS_ABORTED})"$'\n'
        return
    fi

    echo "  warm-up round (unmeasured)..." >&2
    stress_phase_navigation && stress_phase_bursts || true

    if [ -z "$STRESS_ABORTED" ]; then
        # Idle sample right after the Hub re-composed (warm-up ended with BACK -> Hub): shows
        # whether anything (e.g. row marquees) keeps rendering with no input at all.
        echo "  Hub idle (${IDLE_SECONDS} s)..." >&2
        adb_sh dumpsys gfxinfo "$PKG" reset >/dev/null
        sleep "$IDLE_SECONDS"
        DEVICE_REPORT+="  Settings Hub idle (${IDLE_SECONDS} s, just re-opened):"$'\n'
        DEVICE_REPORT+="$(adb_sh dumpsys gfxinfo "$PKG" | gfx_summary)"$'\n'
    fi

    if [ -z "$STRESS_ABORTED" ]; then
        echo "  phase A: sub-screen navigation (${STRESS_ROUNDS} rounds)..." >&2
        adb_sh dumpsys gfxinfo "$PKG" reset >/dev/null
        for _ in $(seq 1 "$STRESS_ROUNDS"); do stress_phase_navigation || break; done
        DEVICE_REPORT+="  Settings sub-screen navigation (${STRESS_ROUNDS} rounds x 8 destinations):"$'\n'
        DEVICE_REPORT+="$(adb_sh dumpsys gfxinfo "$PKG" | gfx_summary)"$'\n'
    fi

    if [ -z "$STRESS_ABORTED" ]; then
        echo "  phase B: D-pad bursts + toggles (${STRESS_ROUNDS} rounds)..." >&2
        adb_sh dumpsys gfxinfo "$PKG" reset >/dev/null
        for _ in $(seq 1 "$STRESS_ROUNDS"); do stress_phase_bursts || break; done
        DEVICE_REPORT+="  Settings D-pad bursts + toggles (${STRESS_ROUNDS} rounds):"$'\n'
        DEVICE_REPORT+="$(adb_sh dumpsys gfxinfo "$PKG" | gfx_summary)"$'\n'
    fi

    if [ -n "$STRESS_ABORTED" ]; then
        DEVICE_REPORT+="  Settings stress ABORTED: ${STRESS_ABORTED} (no further keys were sent;"$'\n'
        DEVICE_REPORT+="    any numbers above cover only the part that ran)"$'\n'
        return
    fi
    mem_report "Settings Hub open, after stress"
    keys KEYCODE_BACK || true # close the Hub (guarded: only while the dialog is focused)
    sleep 1
}

if device_ready; then
    model=$(adb_sh getprop ro.product.model)
    sdk=$(adb_sh getprop ro.build.version.sdk)
    # No early `exit` in awk: under pipefail the writer's SIGPIPE would abort the script (set -e).
    version=$(adb_sh dumpsys package "$PKG" | awk -F= '/versionName=/ && !done { print $2; done = 1 }')
    echo "Device: $model (API $sdk), $PKG $version" >&2

    if [ -n "$STRESS_SETTINGS" ]; then
        run_settings_stress
    else
        # --- Launch time ---------------------------------------------------------------------
        # Mochibun is HOME, so the system relaunches it right after `am force-stop`; `am start -W`
        # then lands on that already-running instance and reports TotalTime 0. Instead, time the
        # system's own relaunch from its "Displayed" line (same clock `am start -W` uses).
        echo "Measuring launch time ($RUNS runs, logcat Displayed)..." >&2
        totals=()
        for i in $(seq 1 "$RUNS"); do
            adb logcat -c 2>/dev/null || true
            adb_sh am force-stop "$PKG" >/dev/null
            t=""
            for poll in $(seq 1 60); do
                # Not the default HOME on this device: nothing relaunches it, so start it ourselves
                [ "$poll" -eq 12 ] && adb_sh am start -n "$ACTIVITY" >/dev/null
                t=$(adb_sh logcat -d -s ActivityTaskManager:I | displayed_ms)
                [ -n "$t" ] && break
                sleep 0.5
            done
            if [ -n "$t" ]; then
                totals+=("$t")
                echo "  run $i: ${t} ms" >&2
            else
                echo "  run $i: no Displayed line within 30 s" >&2
            fi
            sleep 3
        done
        if [ ${#totals[@]} -gt 0 ]; then
            launch_median=$(median "${totals[@]}")
            launch_min=$(printf '%s\n' "${totals[@]}" | sort -n | head -1)
            launch_max=$(printf '%s\n' "${totals[@]}" | sort -n | tail -1)
            DEVICE_REPORT+="  Launch (force-stop -> logcat Displayed, ${#totals[@]} runs): median ${launch_median} ms (min ${launch_min}, max ${launch_max})"$'\n'
            DEVICE_REPORT+="    samples: ${totals[*]}"$'\n'
        else
            DEVICE_REPORT+="  Launch: no ActivityTaskManager Displayed line seen"$'\n'
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

        mem_report "Home, after drawer"
    fi
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
