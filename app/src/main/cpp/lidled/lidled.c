// SPDX-License-Identifier: Apache-2.0
//
// lidled: Mochibun's native clamshell lid LED controller (˶ᵔ ᵕ ᵔ˶)
//
// Direct register programming for the Awinic AW22127 LED controller on the
// Chinoe AT-M120 flip phone (Android 12 API 31, MediaTek MT6761 quad-core 32-bit).
//
// Replaces multi-line shell scripts, subshell while-loops, and shell-side PID tracking.
//
// Usage (requires root):
//   lidled off
//   lidled solid <hex_rgb> [-f|--foreground]
//   lidled pattern <channels_mask> <hex_rgb> [-f|--foreground]
//   lidled shimmer <hex_rgb> <frame_seconds> <cycles> <settle_mode> [-f|--foreground]
//   lidled frames <step_ms> <loops> <last|off> <mask:hex_rgb>... [-f|--foreground]

#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/file.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <time.h>
#include <unistd.h>

#define LED_PATH_REG        "/sys/class/leds/aw22xxx_led/reg"

#define PID_FILE            "/data/local/tmp/lidled.pid"
#define LOCK_FILE           "/data/local/tmp/lidled.lock"

#define WAKE_LOCK_PATH      "/sys/power/wake_lock"
#define WAKE_UNLOCK_PATH    "/sys/power/wake_unlock"
#define WAKE_LOCK_NAME      "lidled_shimmer"

// Physical exterior lid windows mapped to AW22127 channels:
// Window 1: Channel 0
// Window 2: Channel 1
// Window 3: Channel 3
// Window 4: Channel 4
// Window 5: Channel 6
#define MASK_10101 ((1u << 0) | (1u << 3) | (1u << 6))
#define MASK_01010 ((1u << 1) | (1u << 4))
#define MASK_ALL   (MASK_10101 | MASK_01010)

static volatile sig_atomic_t g_stop = 0;

static void sig_handler(int sig) {
    (void)sig;
    g_stop = 1;
}

static inline int write_reg(int fd, uint8_t reg, uint8_t val) {
    char buf[32];
    int len = snprintf(buf, sizeof(buf), "0x%02x 0x%02x\n", reg, val);
    if (len <= 0) return -1;
    if (write(fd, buf, len) != len) {
        return -1;
    }
    return 0;
}

static int write_channel_pattern(int fd, uint32_t active_channels_mask, uint8_t r, uint8_t g, uint8_t b) {
    write_reg(fd, 0xff, 0x00);
    write_reg(fd, 0x05, 0x00);
    write_reg(fd, 0x04, 0x01);
    write_reg(fd, 0x02, 0x00);
    write_reg(fd, 0x02, 0x01);
    write_reg(fd, 0x04, 0x01);
    write_reg(fd, 0x0c, 0x00);
    write_reg(fd, 0xff, 0x01);
    write_reg(fd, 0x00, 0x00);
    write_reg(fd, 0x01, 0x00);
    write_reg(fd, 0x02, 0x00);
    write_reg(fd, 0x03, 0x00);
    write_reg(fd, 0x04, 0x00);
    write_reg(fd, 0x05, 0x03);
    write_reg(fd, 0x06, 0x09);
    write_reg(fd, 0x0b, 0x00);

    for (int ch = 0; ch <= 8; ch++) {
        uint8_t r_reg = 0x10 + ch * 3;
        uint8_t g_reg = r_reg + 1;
        uint8_t b_reg = r_reg + 2;
        if (active_channels_mask & (1u << ch)) {
            write_reg(fd, r_reg, r);
            write_reg(fd, g_reg, g);
            write_reg(fd, b_reg, b);
        } else {
            write_reg(fd, r_reg, 0x00);
            write_reg(fd, g_reg, 0x00);
            write_reg(fd, b_reg, 0x00);
        }
    }

    write_reg(fd, 0x2b, 0x00);
    write_reg(fd, 0x2c, 0xff);
    write_reg(fd, 0xff, 0x00);
    write_reg(fd, 0x05, 0x02);
    write_reg(fd, 0x04, 0x03);
    return 0;
}

static int write_off(int fd) {
    write_reg(fd, 0xff, 0x00);
    write_reg(fd, 0x05, 0x00);
    write_reg(fd, 0x04, 0x00);
    write_reg(fd, 0x02, 0x00);
    write_reg(fd, 0x01, 0x55);
    write_reg(fd, 0xff, 0x01);
    for (int r = 0x10; r <= 0x2a; r++) {
        write_reg(fd, r, 0x00);
    }
    write_reg(fd, 0xff, 0x00);
    write_reg(fd, 0x05, 0x00);
    write_reg(fd, 0x04, 0x00);
    return 0;
}

static int parse_rgb(const char *str, uint8_t *r, uint8_t *g, uint8_t *b) {
    if (!str) return -1;
    if (strncmp(str, "0x", 2) == 0 || strncmp(str, "0X", 2) == 0) str += 2;
    else if (*str == '#') str++;

    char *end = NULL;
    unsigned long val = strtoul(str, &end, 16);
    if (end == str) return -1;
    *r = (uint8_t)((val >> 16) & 0xff);
    *g = (uint8_t)((val >> 8) & 0xff);
    *b = (uint8_t)(val & 0xff);
    return 0;
}

static uint32_t parse_channels_mask(const char *str) {
    if (!str) return 0;
    if (strcmp(str, "all") == 0 || strcmp(str, "solid") == 0 || strcmp(str, "11111") == 0) {
        return MASK_ALL;
    }
    if (strcmp(str, "10101") == 0 || strcmp(str, "glyph") == 0) {
        return MASK_10101;
    }
    if (strcmp(str, "01010") == 0) {
        return MASK_01010;
    }

    size_t len = strlen(str);
    if (len == 5) {
        int is_bin = 1;
        for (size_t i = 0; i < 5; i++) {
            if (str[i] != '0' && str[i] != '1') { is_bin = 0; break; }
        }
        if (is_bin) {
            uint32_t mask = 0;
            static const int win_ch[5] = {0, 1, 3, 4, 6};
            for (int i = 0; i < 5; i++) {
                if (str[i] == '1') {
                    mask |= (1u << win_ch[i]);
                }
            }
            return mask;
        }
    }

    char *end = NULL;
    unsigned long val = strtoul(str, &end, 0);
    if (end != str) return (uint32_t)val;
    return 0;
}

static void acquire_kernel_wake_lock(void) {
    int fd = open(WAKE_LOCK_PATH, O_WRONLY);
    if (fd >= 0) {
        ssize_t ret = write(fd, WAKE_LOCK_NAME, strlen(WAKE_LOCK_NAME));
        (void)ret;
        close(fd);
    }
}

static void release_kernel_wake_lock(void) {
    int fd = open(WAKE_UNLOCK_PATH, O_WRONLY);
    if (fd >= 0) {
        ssize_t ret = write(fd, WAKE_LOCK_NAME, strlen(WAKE_LOCK_NAME));
        (void)ret;
        close(fd);
    }
}

static void stop_previous_daemon(void) {
    FILE *fp = fopen(PID_FILE, "r");
    if (!fp) return;
    pid_t pid = 0;
    if (fscanf(fp, "%d", &pid) == 1 && pid > 1 && pid != getpid()) {
        fclose(fp);
        char comm_path[64];
        snprintf(comm_path, sizeof(comm_path), "/proc/%d/cmdline", pid);
        FILE *cmd_fp = fopen(comm_path, "r");
        int is_our_process = 0;
        if (cmd_fp) {
            char cmdline[128];
            size_t n = fread(cmdline, 1, sizeof(cmdline) - 1, cmd_fp);
            cmdline[n] = '\0';
            fclose(cmd_fp);
            if (strstr(cmdline, "lidled") != NULL) {
                is_our_process = 1;
            }
        }
        if (is_our_process) {
            kill(pid, SIGTERM);
            for (int i = 0; i < 20; i++) {
                usleep(5000); // 5 ms
                if (kill(pid, 0) != 0) break;
            }
            if (kill(pid, 0) == 0) {
                kill(pid, SIGKILL);
                usleep(2000);
            }
        }
        release_kernel_wake_lock();
        unlink(PID_FILE);
    } else {
        fclose(fp);
        unlink(PID_FILE);
    }
}

static int interruptible_sleep(const struct timespec *ts) {
    struct timespec rem = *ts;
    while (!g_stop) {
        if (nanosleep(&rem, &rem) == 0) {
            return 0;
        }
        if (errno == EINTR) {
            if (g_stop) return -1;
        } else {
            return -1;
        }
    }
    return -1;
}

static void remove_pid_if_ours(pid_t my_pid) {
    FILE *fp = fopen(PID_FILE, "r");
    if (fp) {
        pid_t cur_pid = 0;
        if (fscanf(fp, "%d", &cur_pid) == 1 && cur_pid == my_pid) {
            fclose(fp);
            unlink(PID_FILE);
            return;
        }
        fclose(fp);
    }
}

static void setup_signal_handlers(void) {
    signal(SIGHUP, SIG_IGN);
    signal(SIGPIPE, SIG_IGN);

    struct sigaction sa;
    memset(&sa, 0, sizeof(sa));
    sa.sa_handler = sig_handler;
    sigaction(SIGTERM, &sa, NULL);
    sigaction(SIGINT, &sa, NULL);
}

static void run_static_hold(int reg_fd, uint32_t active_channels_mask, uint8_t r, uint8_t g, uint8_t b) {
    setup_signal_handlers();
    write_channel_pattern(reg_fd, active_channels_mask, r, g, b);

    struct timespec hold_ts = { .tv_sec = 1, .tv_nsec = 0 };
    while (!g_stop) {
        interruptible_sleep(&hold_ts);
    }

    remove_pid_if_ours(getpid());
}

static void run_shimmer_loop(int reg_fd, uint8_t r, uint8_t g, uint8_t b,
                             double frame_seconds, int cycles, const char *settle_mode) {
    setup_signal_handlers();
    acquire_kernel_wake_lock();

    struct timespec ts;
    ts.tv_sec = (time_t)frame_seconds;
    ts.tv_nsec = (long)((frame_seconds - (double)ts.tv_sec) * 1e9);
    if (ts.tv_nsec < 0) ts.tv_nsec = 0;
    if (ts.tv_nsec >= 1000000000L) ts.tv_nsec = 999999999L;

    for (int i = 0; i < cycles && !g_stop; i++) {
        write_channel_pattern(reg_fd, MASK_10101, r, g, b);
        if (interruptible_sleep(&ts) != 0) break;
        write_channel_pattern(reg_fd, MASK_01010, r, g, b);
        if (interruptible_sleep(&ts) != 0) break;
    }

    // Release kernel wake lock once active shimmer cycles complete,
    // allowing the SoC to enter deep sleep while holding the static frame.
    release_kernel_wake_lock();

    if (!g_stop) {
        if (strcmp(settle_mode, "off") == 0) {
            write_off(reg_fd);
        } else {
            uint32_t mask = 0;
            if (strcmp(settle_mode, "solid") == 0 || strcmp(settle_mode, "all") == 0 || strcmp(settle_mode, "11111") == 0) {
                mask = MASK_ALL;
            } else if (strcmp(settle_mode, "10101") == 0 || strcmp(settle_mode, "glyph") == 0) {
                mask = MASK_10101;
            } else {
                mask = parse_channels_mask(settle_mode);
                if (!mask) mask = MASK_ALL;
            }
            write_channel_pattern(reg_fd, mask, r, g, b);

            struct timespec hold_ts = { .tv_sec = 1, .tv_nsec = 0 };
            while (!g_stop) {
                interruptible_sleep(&hold_ts);
            }
        }
    }

    remove_pid_if_ours(getpid());
}

// ---- Step animations (Mochibun Lid LED Studio) ----
//
// Every step is written once with write_channel_pattern() from this one daemon. Separate
// `pattern` calls per step re-spawn the daemon and re-init the chip each time, which on the
// AT-M120 drops frames (the previous frame stays lit) and flickers; one writer per animation
// is reliable for every mask (verified on device).

#define MAX_FRAMES      16
#define MAX_FRAME_LOOPS 120
#define MIN_STEP_MS     50
#define MAX_STEP_MS     5000

typedef struct {
    uint32_t mask;
    uint8_t r, g, b;
} led_frame_t;

// "<5-char window mask>:<hex_rgb>", e.g. "10101:0xff1493". "00000" = all windows dark.
static int parse_frame(const char *arg, led_frame_t *out) {
    const char *colon = strchr(arg, ':');
    if (!colon || colon == arg || colon - arg >= 16) return -1;
    char mask_str[16];
    memcpy(mask_str, arg, (size_t)(colon - arg));
    mask_str[colon - arg] = '\0';
    out->mask = parse_channels_mask(mask_str);
    return parse_rgb(colon + 1, &out->r, &out->g, &out->b);
}

static void timespec_add_ms(struct timespec *ts, long ms) {
    ts->tv_sec += ms / 1000;
    ts->tv_nsec += (ms % 1000) * 1000000L;
    if (ts->tv_nsec >= 1000000000L) {
        ts->tv_sec += 1;
        ts->tv_nsec -= 1000000000L;
    }
}

// Absolute deadlines keep the step rate steady regardless of how long a write takes.
static int sleep_until(const struct timespec *deadline) {
    while (!g_stop) {
        int rc = clock_nanosleep(CLOCK_MONOTONIC, TIMER_ABSTIME, deadline, NULL);
        if (rc == 0) return 0;
        if (rc != EINTR) return -1;
    }
    return -1;
}

static void run_frames_loop(int reg_fd, const led_frame_t *frames, int count,
                            int step_ms, int loops, int settle_last) {
    setup_signal_handlers();
    acquire_kernel_wake_lock();

    struct timespec next;
    clock_gettime(CLOCK_MONOTONIC, &next);
    for (int loop = 0; loop < loops && !g_stop; loop++) {
        for (int i = 0; i < count && !g_stop; i++) {
            write_channel_pattern(reg_fd, frames[i].mask, frames[i].r, frames[i].g, frames[i].b);
            timespec_add_ms(&next, step_ms);
            if (sleep_until(&next) != 0) break;
        }
    }

    // Same as shimmer: let the SoC sleep while the chip holds the resting frame.
    release_kernel_wake_lock();

    if (!g_stop) {
        if (settle_last) {
            const led_frame_t *last = &frames[count - 1];
            write_channel_pattern(reg_fd, last->mask, last->r, last->g, last->b);
            struct timespec hold_ts = { .tv_sec = 1, .tv_nsec = 0 };
            while (!g_stop) {
                interruptible_sleep(&hold_ts);
            }
        } else {
            write_off(reg_fd);
        }
    }

    remove_pid_if_ours(getpid());
}

static int spawn_daemon_or_run(int lock_fd, int foreground) {
    if (foreground) {
        FILE *pfp = fopen(PID_FILE, "w");
        if (pfp) {
            fprintf(pfp, "%d\n", getpid());
            fclose(pfp);
        }
        if (lock_fd >= 0) { flock(lock_fd, LOCK_UN); close(lock_fd); }

        int reg_fd = open(LED_PATH_REG, O_WRONLY);
        if (reg_fd < 0) {
            fprintf(stderr, "lidled: failed to open %s: %s\n", LED_PATH_REG, strerror(errno));
            remove_pid_if_ours(getpid());
            return -1;
        }
        return reg_fd;
    }

    pid_t child = fork();
    if (child < 0) {
        fprintf(stderr, "lidled: fork failed: %s\n", strerror(errno));
        if (lock_fd >= 0) { flock(lock_fd, LOCK_UN); close(lock_fd); }
        return -1;
    }
    if (child > 0) {
        // Parent: record child PID, release lock, exit cleanly
        FILE *pfp = fopen(PID_FILE, "w");
        if (pfp) {
            fprintf(pfp, "%d\n", child);
            fclose(pfp);
        }
        if (lock_fd >= 0) { flock(lock_fd, LOCK_UN); close(lock_fd); }
        exit(0);
    }

    // Child process:
    if (lock_fd >= 0) { close(lock_fd); }
    setsid();

    signal(SIGHUP, SIG_IGN);
    signal(SIGPIPE, SIG_IGN);

    int devnull = open("/dev/null", O_RDWR);
    if (devnull >= 0) {
        dup2(devnull, STDIN_FILENO);
        dup2(devnull, STDOUT_FILENO);
        dup2(devnull, STDERR_FILENO);
        if (devnull > 2) close(devnull);
    }

    int reg_fd = open(LED_PATH_REG, O_WRONLY);
    if (reg_fd < 0) {
        remove_pid_if_ours(getpid());
        exit(1);
    }
    return reg_fd;
}

static void print_usage(const char *prog) {
    fprintf(stderr,
        "Usage: %s <command> [args...]\n"
        "Commands:\n"
        "  off                                        Shut down all channels and effects\n"
        "  solid <hex_rgb> [-f|--foreground]          Light all 5 windows in solid RGB\n"
        "  pattern <channels_mask> <hex_rgb> [-f|--foreground]\n"
        "                                             Light selected windows (e.g. 10101, 01010, all)\n"
        "  shimmer <hex_rgb> <frame_sec> <cycles> <settle_mode> [-f|--foreground]\n"
        "                                             Alternating 10101 <-> 01010 shimmer\n"
        "  frames <step_ms> <loops> <last|off> <mask:hex_rgb>... [-f|--foreground]\n"
        "                                             Step animation (up to 16 frames), then hold\n"
        "                                             the last frame or switch off\n",
        prog);
}

int main(int argc, char **argv) {
    if (argc < 2) {
        print_usage(argv[0]);
        return 1;
    }

    const char *cmd = argv[1];

    if (strcmp(cmd, "help") == 0 || strcmp(cmd, "--help") == 0 || strcmp(cmd, "-h") == 0) {
        print_usage(argv[0]);
        return 0;
    }

    int lock_fd = open(LOCK_FILE, O_CREAT | O_RDWR, 0666);
    if (lock_fd >= 0) {
        flock(lock_fd, LOCK_EX);
    }

    // Stop any running daemon (shimmer or static) before applying any new state
    stop_previous_daemon();

    if (strcmp(cmd, "off") == 0 || strcmp(cmd, "stop") == 0) {
        int reg_fd = open(LED_PATH_REG, O_WRONLY);
        if (reg_fd < 0) {
            fprintf(stderr, "lidled: failed to open %s: %s\n", LED_PATH_REG, strerror(errno));
            if (lock_fd >= 0) { flock(lock_fd, LOCK_UN); close(lock_fd); }
            return 1;
        }
        write_off(reg_fd);
        close(reg_fd);
        if (lock_fd >= 0) { flock(lock_fd, LOCK_UN); close(lock_fd); }
        return 0;
    }

    if (strcmp(cmd, "solid") == 0) {
        if (argc < 3) {
            fprintf(stderr, "Usage: %s solid <hex_rgb> [-f|--foreground]\n", argv[0]);
            if (lock_fd >= 0) { flock(lock_fd, LOCK_UN); close(lock_fd); }
            return 1;
        }
        uint32_t mask = parse_channels_mask("11111");
        uint8_t r = 0, g = 0, b = 0;
        if (parse_rgb(argv[2], &r, &g, &b) != 0) {
            fprintf(stderr, "lidled: invalid hex color '%s'\n", argv[2]);
            if (lock_fd >= 0) { flock(lock_fd, LOCK_UN); close(lock_fd); }
            return 1;
        }
        int foreground = 0;
        if (argc >= 4 && (strcmp(argv[3], "-f") == 0 || strcmp(argv[3], "--foreground") == 0)) {
            foreground = 1;
        }
        int reg_fd = spawn_daemon_or_run(lock_fd, foreground);
        if (reg_fd < 0) return 1;
        run_static_hold(reg_fd, mask, r, g, b);
        close(reg_fd);
        return 0;
    }

    if (strcmp(cmd, "pattern") == 0) {
        if (argc < 4) {
            fprintf(stderr, "Usage: %s pattern <channels_mask> <hex_rgb> [-f|--foreground]\n", argv[0]);
            if (lock_fd >= 0) { flock(lock_fd, LOCK_UN); close(lock_fd); }
            return 1;
        }
        uint32_t mask = parse_channels_mask(argv[2]);
        uint8_t r = 0, g = 0, b = 0;
        if (parse_rgb(argv[3], &r, &g, &b) != 0) {
            fprintf(stderr, "lidled: invalid hex color '%s'\n", argv[3]);
            if (lock_fd >= 0) { flock(lock_fd, LOCK_UN); close(lock_fd); }
            return 1;
        }
        int foreground = 0;
        if (argc >= 5 && (strcmp(argv[4], "-f") == 0 || strcmp(argv[4], "--foreground") == 0)) {
            foreground = 1;
        }
        int reg_fd = spawn_daemon_or_run(lock_fd, foreground);
        if (reg_fd < 0) return 1;
        run_static_hold(reg_fd, mask, r, g, b);
        close(reg_fd);
        return 0;
    }

    if (strcmp(cmd, "shimmer") == 0) {
        if (argc < 6) {
            fprintf(stderr, "Usage: %s shimmer <hex_rgb> <frame_seconds> <cycles> <settle_mode> [-f|--foreground]\n", argv[0]);
            if (lock_fd >= 0) { flock(lock_fd, LOCK_UN); close(lock_fd); }
            return 1;
        }
        uint8_t r = 0, g = 0, b = 0;
        if (parse_rgb(argv[2], &r, &g, &b) != 0) {
            fprintf(stderr, "lidled: invalid hex color '%s'\n", argv[2]);
            if (lock_fd >= 0) { flock(lock_fd, LOCK_UN); close(lock_fd); }
            return 1;
        }
        double frame_sec = strtod(argv[3], NULL);
        if (frame_sec <= 0.0) frame_sec = 0.8;
        int cycles = atoi(argv[4]);
        if (cycles <= 0) cycles = 1;
        const char *settle_mode = argv[5];

        int foreground = 0;
        if (argc >= 7 && (strcmp(argv[6], "-f") == 0 || strcmp(argv[6], "--foreground") == 0)) {
            foreground = 1;
        }

        int reg_fd = spawn_daemon_or_run(lock_fd, foreground);
        if (reg_fd < 0) return 1;
        run_shimmer_loop(reg_fd, r, g, b, frame_sec, cycles, settle_mode);
        close(reg_fd);
        return 0;
    }

    if (strcmp(cmd, "frames") == 0) {
        int end = argc;
        int foreground = 0;
        if (argc >= 2 && (strcmp(argv[argc - 1], "-f") == 0 || strcmp(argv[argc - 1], "--foreground") == 0)) {
            foreground = 1;
            end--;
        }
        if (end < 6) {
            fprintf(stderr, "Usage: %s frames <step_ms> <loops> <last|off> <mask:hex_rgb>... [-f|--foreground]\n", argv[0]);
            if (lock_fd >= 0) { flock(lock_fd, LOCK_UN); close(lock_fd); }
            return 1;
        }
        int step_ms = atoi(argv[2]);
        if (step_ms < MIN_STEP_MS) step_ms = MIN_STEP_MS;
        if (step_ms > MAX_STEP_MS) step_ms = MAX_STEP_MS;
        int loops = atoi(argv[3]);
        if (loops < 1) loops = 1;
        if (loops > MAX_FRAME_LOOPS) loops = MAX_FRAME_LOOPS;
        int settle_last = strcmp(argv[4], "last") == 0;

        led_frame_t frames[MAX_FRAMES];
        int count = 0;
        for (int a = 5; a < end && count < MAX_FRAMES; a++) {
            if (parse_frame(argv[a], &frames[count]) != 0) {
                fprintf(stderr, "lidled: invalid frame '%s' (want <mask>:<hex_rgb>)\n", argv[a]);
                if (lock_fd >= 0) { flock(lock_fd, LOCK_UN); close(lock_fd); }
                return 1;
            }
            count++;
        }

        int reg_fd = spawn_daemon_or_run(lock_fd, foreground);
        if (reg_fd < 0) return 1;
        run_frames_loop(reg_fd, frames, count, step_ms, loops, settle_last);
        close(reg_fd);
        return 0;
    }

    fprintf(stderr, "lidled: unknown command '%s'\n", cmd);
    print_usage(argv[0]);
    if (lock_fd >= 0) { flock(lock_fd, LOCK_UN); close(lock_fd); }
    return 1;
}
