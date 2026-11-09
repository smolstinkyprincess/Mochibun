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
//   lidled solid <hex_rgb>
//   lidled pattern <channels_mask> <hex_rgb>
//   lidled shimmer <hex_rgb> <frame_seconds> <cycles> <settle_mode> [-f|--foreground]

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

static void stop_previous_shimmer(void) {
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

static void run_shimmer_loop(int reg_fd, uint8_t r, uint8_t g, uint8_t b,
                             double frame_seconds, int cycles, const char *settle_mode) {
    signal(SIGHUP, SIG_IGN);
    signal(SIGPIPE, SIG_IGN);

    struct sigaction sa;
    memset(&sa, 0, sizeof(sa));
    sa.sa_handler = sig_handler;
    sigaction(SIGTERM, &sa, NULL);
    sigaction(SIGINT, &sa, NULL);

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

    if (!g_stop) {
        if (strcmp(settle_mode, "off") == 0) {
            write_off(reg_fd);
        } else if (strcmp(settle_mode, "solid") == 0 || strcmp(settle_mode, "all") == 0 || strcmp(settle_mode, "11111") == 0) {
            write_channel_pattern(reg_fd, MASK_ALL, r, g, b);
        } else if (strcmp(settle_mode, "10101") == 0 || strcmp(settle_mode, "glyph") == 0) {
            write_channel_pattern(reg_fd, MASK_10101, r, g, b);
        } else {
            uint32_t mask = parse_channels_mask(settle_mode);
            write_channel_pattern(reg_fd, mask ? mask : MASK_ALL, r, g, b);
        }
    }

    remove_pid_if_ours(getpid());
}

static void print_usage(const char *prog) {
    fprintf(stderr,
        "Usage: %s <command> [args...]\n"
        "Commands:\n"
        "  off                                        Shut down all channels and effects\n"
        "  solid <hex_rgb>                            Light all 5 windows in solid RGB\n"
        "  pattern <channels_mask> <hex_rgb>          Light selected windows (e.g. 10101, 01010, all)\n"
        "  shimmer <hex_rgb> <frame_sec> <cycles> <settle_mode> [-f|--foreground]\n"
        "                                             Alternating 10101 <-> 01010 shimmer\n",
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

    // Stop any running shimmer before applying any new state
    stop_previous_shimmer();

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
            fprintf(stderr, "Usage: %s solid <hex_rgb>\n", argv[0]);
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
        int reg_fd = open(LED_PATH_REG, O_WRONLY);
        if (reg_fd < 0) {
            fprintf(stderr, "lidled: failed to open %s: %s\n", LED_PATH_REG, strerror(errno));
            if (lock_fd >= 0) { flock(lock_fd, LOCK_UN); close(lock_fd); }
            return 1;
        }
        write_channel_pattern(reg_fd, mask, r, g, b);
        close(reg_fd);
        if (lock_fd >= 0) { flock(lock_fd, LOCK_UN); close(lock_fd); }
        return 0;
    }

    if (strcmp(cmd, "pattern") == 0) {
        if (argc < 4) {
            fprintf(stderr, "Usage: %s pattern <channels_mask> <hex_rgb>\n", argv[0]);
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
        int reg_fd = open(LED_PATH_REG, O_WRONLY);
        if (reg_fd < 0) {
            fprintf(stderr, "lidled: failed to open %s: %s\n", LED_PATH_REG, strerror(errno));
            if (lock_fd >= 0) { flock(lock_fd, LOCK_UN); close(lock_fd); }
            return 1;
        }
        write_channel_pattern(reg_fd, mask, r, g, b);
        close(reg_fd);
        if (lock_fd >= 0) { flock(lock_fd, LOCK_UN); close(lock_fd); }
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
        if (argc >= 7) {
            if (strcmp(argv[6], "-f") == 0 || strcmp(argv[6], "--foreground") == 0) {
                foreground = 1;
            }
        }

        if (foreground) {
            // Write our own PID
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
                return 1;
            }
            run_shimmer_loop(reg_fd, r, g, b, frame_sec, cycles, settle_mode);
            close(reg_fd);
            return 0;
        } else {
            // Fork into background daemon
            pid_t child = fork();
            if (child < 0) {
                fprintf(stderr, "lidled: fork failed: %s\n", strerror(errno));
                if (lock_fd >= 0) { flock(lock_fd, LOCK_UN); close(lock_fd); }
                return 1;
            }
            if (child > 0) {
                // Parent: save child PID, unlock, and exit immediately
                FILE *pfp = fopen(PID_FILE, "w");
                if (pfp) {
                    fprintf(pfp, "%d\n", child);
                    fclose(pfp);
                }
                if (lock_fd >= 0) { flock(lock_fd, LOCK_UN); close(lock_fd); }
                return 0;
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
                return 1;
            }
            run_shimmer_loop(reg_fd, r, g, b, frame_sec, cycles, settle_mode);
            close(reg_fd);
            return 0;
        }
    }

    fprintf(stderr, "lidled: unknown command '%s'\n", cmd);
    print_usage(argv[0]);
    if (lock_fd >= 0) { flock(lock_fd, LOCK_UN); close(lock_fd); }
    return 1;
}
