// SPDX-License-Identifier: Apache-2.0
//
// evinject: Mochibun's tiny root-side touch pal (˶ᵔ ᵕ ᵔ˶)
//
// Why it exists: `input tap` boots a whole app_process every call. Writing
// struct input_event frames straight into the touchscreen's evdev node takes
// the same kernel path as `sendevent` (evdev_write -> input_inject_event), and
// Android's InputReader can't tell our frames from a real finger.
//
// Single-threaded, no malloc in the hot path, sleeps in poll() when idle.
//
// Usage (needs root):
//   evinject info  [--device PATH]
//   evinject tap   X Y [HOLD_MS]            [--screen WxH] [--device PATH] [--slot N]
//   evinject swipe X1 Y1 X2 Y2 [DUR_MS]     [--screen WxH] [--device PATH] [--slot N]
//   evinject daemon --stdin                 [--screen WxH] [--device PATH] [--slot N]

#define _GNU_SOURCE
#include <ctype.h>
#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <linux/input.h>
#include <poll.h>
#include <signal.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <sys/ioctl.h>
#include <sys/prctl.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <time.h>
#include <unistd.h>

#ifndef INPUT_PROP_DIRECT
#define INPUT_PROP_DIRECT 0x01
#endif
#ifndef INPUT_PROP_MAX
#define INPUT_PROP_MAX 0x1f
#endif
#ifndef EVIOCGPROP
#define EVIOCGPROP(len) _IOC(_IOC_READ, 'E', 0x09, len)
#endif

#define EVI_MAGIC          0xB7
#define STEP_MS            8        // swipe step, ~120 Hz; the panel only needs 60
#define TOUCH_WATCHDOG_MS  10000    // finger down + silence this long => let go
#define MAX_FRAME          16

// ---- wire format: 24 bytes, little-endian. Mirror of RootTouchInjector.kt ----
enum {
    OP_DOWN = 1, OP_MOVE = 2, OP_UP = 3, OP_TAP = 4, OP_SWIPE = 5,
    OP_RESET = 6, OP_SCREEN = 7, OP_QUIT = 8, OP_PING = 9,
};

struct evi_pkt {
    uint8_t  magic;
    uint8_t  op;
    uint16_t pad;
    int32_t  a, b, c, d, e;
} __attribute__((packed));
_Static_assert(sizeof(struct evi_pkt) == 24, "packet must stay 24 bytes");

// op       a    b    c        d    e
// DOWN     x    y
// MOVE     x    y
// UP
// TAP      x    y    holdMs
// SWIPE    x1   y1   x2       y2   durMs
// RESET    (release any finger we have down)
// SCREEN   w    h    (pixel size of the coordinate space the app uses)
// QUIT / PING

enum proto { PROTO_B, PROTO_A, PROTO_ST };

struct touch_dev {
    int  fd;
    char path[64];
    char name[80];
    enum proto proto;
    int  score, direct;
    int  xmin, xmax, ymin, ymax;
    int  nslots, slot;
    int  has_btn_touch, has_tool_finger, has_abs_xy, has_trkid, trk_max;
    int  has_pressure, pressure;   // value we inject; always > 0 on purpose
    int  has_major, major;
    // runtime state
    int  scr_w, scr_h;
    int  touching, trkid, last_rx, last_ry;
};

struct frame {
    struct input_event ev[MAX_FRAME];
    int n;
};

static volatile sig_atomic_t g_quit;
static void on_signal(int sig) { (void)sig; g_quit = 1; }

// ---------------------------------------------------------------- helpers ---

static long long now_ns(void)
{
    struct timespec t;
    clock_gettime(CLOCK_MONOTONIC, &t);
    return (long long)t.tv_sec * 1000000000LL + t.tv_nsec;
}

static void sleep_until(long long deadline_ns)
{
    struct timespec t = { (time_t)(deadline_ns / 1000000000LL), (long)(deadline_ns % 1000000000LL) };
    while (clock_nanosleep(CLOCK_MONOTONIC, TIMER_ABSTIME, &t, NULL) == EINTR) {
        if (g_quit) break;
    }
}

static int bit_set(const unsigned char *bits, int bit)
{
    return (bits[bit >> 3] >> (bit & 7)) & 1;
}

// middle of the axis range, but never 0: pressure/size of 0 reads as "hovering"
static int mid_positive(const struct input_absinfo *ai)
{
    int v = ai->minimum + (ai->maximum - ai->minimum) / 2;
    if (v < 1) v = 1;
    if (ai->maximum >= 1 && v > ai->maximum) v = ai->maximum;
    return v;
}

static const char *proto_name(enum proto p)
{
    return p == PROTO_B ? "B" : p == PROTO_A ? "A" : "single";
}

static void put(struct frame *f, uint16_t type, uint16_t code, int32_t value)
{
    if (f->n >= MAX_FRAME) return;
    struct input_event *e = &f->ev[f->n++];
    memset(e, 0, sizeof *e);   // kernel stamps the time itself; sizeof is 16 on armv7
    e->type = type;
    e->code = code;
    e->value = value;
}

static int flush(struct touch_dev *d, struct frame *f)
{
    size_t len = (size_t)f->n * sizeof(struct input_event);
    ssize_t w;
    do { w = write(d->fd, f->ev, len); } while (w < 0 && errno == EINTR);
    f->n = 0;
    return w == (ssize_t)len ? 0 : -1;
}

// logical pixel (0..span-1) -> raw digitizer units
static int map_axis(int v, int span_px, int rmin, int rmax)
{
    if (span_px <= 1) return rmin;
    if (v < 0) v = 0;
    if (v > span_px - 1) v = span_px - 1;
    return rmin + (int)(((long long)v * (rmax - rmin) + (span_px - 1) / 2) / (span_px - 1));
}

// ----------------------------------------------------------- node probing ---

// Opens `path`; on success returns 0 with d->fd open and d fully described.
static int probe_node(const char *path, struct touch_dev *d)
{
    memset(d, 0, sizeof *d);
    d->fd = open(path, O_RDWR | O_CLOEXEC);
    if (d->fd < 0) return -1;
    snprintf(d->path, sizeof d->path, "%s", path);

    unsigned char ev[EV_MAX / 8 + 1] = {0}, abs_[ABS_MAX / 8 + 1] = {0};
    unsigned char key[KEY_MAX / 8 + 1] = {0}, prop[INPUT_PROP_MAX / 8 + 1] = {0};
    ioctl(d->fd, EVIOCGBIT(0, sizeof ev), ev);
    ioctl(d->fd, EVIOCGNAME(sizeof d->name - 1), d->name);

    int ok = bit_set(ev, EV_ABS) && bit_set(ev, EV_SYN) && !bit_set(ev, EV_REL); // no mice
    if (!ok) goto reject;

    ioctl(d->fd, EVIOCGBIT(EV_ABS, sizeof abs_), abs_);
    ioctl(d->fd, EVIOCGBIT(EV_KEY, sizeof key), key);
    ioctl(d->fd, EVIOCGPROP(sizeof prop), prop);   // old kernels may refuse; fine

    int mt = bit_set(abs_, ABS_MT_POSITION_X) && bit_set(abs_, ABS_MT_POSITION_Y);
    int st = bit_set(abs_, ABS_X) && bit_set(abs_, ABS_Y);
    if (!mt && !st) goto reject;                    // gamepads etc. fall out here too

    struct input_absinfo ai;
    int ax = mt ? ABS_MT_POSITION_X : ABS_X, ay = mt ? ABS_MT_POSITION_Y : ABS_Y;
    ioctl(d->fd, EVIOCGABS(ax), &ai); d->xmin = ai.minimum; d->xmax = ai.maximum;
    ioctl(d->fd, EVIOCGABS(ay), &ai); d->ymin = ai.minimum; d->ymax = ai.maximum;
    if (d->xmax <= d->xmin || d->ymax <= d->ymin) goto reject;

    d->proto = !mt ? PROTO_ST : bit_set(abs_, ABS_MT_SLOT) ? PROTO_B : PROTO_A;
    if (d->proto == PROTO_B) {
        ioctl(d->fd, EVIOCGABS(ABS_MT_SLOT), &ai);
        d->nslots = ai.maximum + 1;
    }
    d->has_trkid = mt && bit_set(abs_, ABS_MT_TRACKING_ID);
    if (d->has_trkid) { ioctl(d->fd, EVIOCGABS(ABS_MT_TRACKING_ID), &ai); d->trk_max = ai.maximum; }
    d->has_abs_xy = mt && st;

    int pcode = (mt && bit_set(abs_, ABS_MT_PRESSURE)) ? ABS_MT_PRESSURE :
                bit_set(abs_, ABS_PRESSURE) ? ABS_PRESSURE : 0;
    if (pcode != 0) {
        ioctl(d->fd, EVIOCGABS(pcode), &ai);
        d->has_pressure = 1; d->pressure = mid_positive(&ai);
    }
    if (mt && bit_set(abs_, ABS_MT_TOUCH_MAJOR)) {
        ioctl(d->fd, EVIOCGABS(ABS_MT_TOUCH_MAJOR), &ai);
        d->has_major = 1; d->major = mid_positive(&ai);
    }
    d->has_btn_touch   = bit_set(key, BTN_TOUCH);
    d->has_tool_finger = bit_set(key, BTN_TOOL_FINGER);

    // score: which node is most likely THE touchscreen (prefer Protocol B slots over legacy/virtual)
    d->direct = (prop[INPUT_PROP_DIRECT >> 3] >> (INPUT_PROP_DIRECT & 7)) & 1;
    d->score = (d->direct ? 100 : 0) + (mt ? 20 : 0) + (d->has_btn_touch ? 10 : 0) +
               (d->proto == PROTO_B ? 10 : 0);
    static const char *const likely[] = { "touch", "tpd", "goodix", "fts", "ft5", "gt9", "himax",
        "ilitek", "focaltech", "synaptics", "hynitron", "chipone", "novatek", "gsl", NULL };
    for (int i = 0; likely[i]; i++)
        if (strcasestr(d->name, likely[i])) { d->score += 5; break; }
    if (strcasestr(d->name, "uinput") || strcasestr(d->name, "virtual") || strcasestr(d->name, "mochibun"))
        d->score -= 1000;                           // never inject into someone's fake device
    return 0;

reject:
    close(d->fd);
    d->fd = -1;
    return -1;
}

static void print_dev(const struct touch_dev *d)
{
    printf("node=%s name=\"%s\" score=%d direct=%d proto=%s x=%d..%d y=%d..%d slots=%d "
           "btn_touch=%d pressure=%d major=%d trkid_max=%d\n",
           d->path, d->name, d->score, d->direct, proto_name(d->proto), d->xmin, d->xmax,
           d->ymin, d->ymax, d->nslots, d->has_btn_touch, d->has_pressure ? d->pressure : 0,
           d->has_major ? d->major : 0, d->trk_max);
}

static int pick_device(struct touch_dev *best, const char *override_path, int verbose)
{
    best->fd = -1;
    if (override_path) {
        if (probe_node(override_path, best) != 0) return -1;
        if (verbose) print_dev(best);
        return 0;
    }
    DIR *dir = opendir("/dev/input");
    if (!dir) return -1;
    struct dirent *de;
    while ((de = readdir(dir))) {
        if (strncmp(de->d_name, "event", 5) != 0) continue;
        char path[64];
        snprintf(path, sizeof path, "/dev/input/%s", de->d_name);
        struct touch_dev c;
        if (probe_node(path, &c) != 0) continue;
        if (verbose) print_dev(&c);
        if (c.score > 0 && (best->fd < 0 || c.score > best->score)) {
            if (best->fd >= 0) close(best->fd);
            *best = c;
        } else {
            close(c.fd);
        }
    }
    closedir(dir);
    return best->fd >= 0 ? 0 : -1;
}

// ------------------------------------------------------------ touch frames ---

// One contact frame (down edge or move). Key edges go first, like real drivers.
static void build_contact(struct touch_dev *d, struct frame *f, int rx, int ry, int first)
{
    f->n = 0;
    if (first) {
        if (d->has_btn_touch)   put(f, EV_KEY, BTN_TOUCH, 1);
        if (d->has_tool_finger) put(f, EV_KEY, BTN_TOOL_FINGER, 1);
    }
    switch (d->proto) {
    case PROTO_B:
        put(f, EV_ABS, ABS_MT_SLOT, d->slot);
        if (first) put(f, EV_ABS, ABS_MT_TRACKING_ID, d->trkid);
        put(f, EV_ABS, ABS_MT_POSITION_X, rx);
        put(f, EV_ABS, ABS_MT_POSITION_Y, ry);
        if (d->has_pressure) put(f, EV_ABS, ABS_MT_PRESSURE, d->pressure);
        if (d->has_major)    put(f, EV_ABS, ABS_MT_TOUCH_MAJOR, d->major);
        break;
    case PROTO_A:   // stateless: every frame carries the whole contact, then SYN_MT_REPORT
        if (d->has_trkid) put(f, EV_ABS, ABS_MT_TRACKING_ID, d->trkid);
        put(f, EV_ABS, ABS_MT_POSITION_X, rx);
        put(f, EV_ABS, ABS_MT_POSITION_Y, ry);
        if (d->has_pressure) put(f, EV_ABS, ABS_MT_PRESSURE, d->pressure);
        if (d->has_major)    put(f, EV_ABS, ABS_MT_TOUCH_MAJOR, d->major);
        put(f, EV_SYN, SYN_MT_REPORT, 0);
        break;
    case PROTO_ST:
        put(f, EV_ABS, ABS_X, rx);
        put(f, EV_ABS, ABS_Y, ry);
        if (d->has_pressure) put(f, EV_ABS, ABS_PRESSURE, d->pressure);
        break;
    }
    if (d->proto != PROTO_ST && d->has_abs_xy) {   // emulated single-touch axes, like kernel pointer emulation
        put(f, EV_ABS, ABS_X, rx);
        put(f, EV_ABS, ABS_Y, ry);
    }
    put(f, EV_SYN, SYN_REPORT, 0);
}

static void touch_up(struct touch_dev *d, int force)
{
    if (!d->touching && !force) return;
    struct frame f;
    f.n = 0;
    if (d->has_btn_touch)   put(&f, EV_KEY, BTN_TOUCH, 0);
    if (d->has_tool_finger) put(&f, EV_KEY, BTN_TOOL_FINGER, 0);
    switch (d->proto) {
    case PROTO_B:
        put(&f, EV_ABS, ABS_MT_SLOT, d->slot);
        put(&f, EV_ABS, ABS_MT_TRACKING_ID, -1);
        break;
    case PROTO_A:
        put(&f, EV_SYN, SYN_MT_REPORT, 0);      // an empty MT frame = nobody's touching
        break;
    case PROTO_ST:
        if (d->has_pressure) put(&f, EV_ABS, ABS_PRESSURE, 0);
        break;
    }
    put(&f, EV_SYN, SYN_REPORT, 0);
    flush(d, &f);
    d->touching = 0;
}

static void touch_down(struct touch_dev *d, int x, int y)
{
    if (d->touching) touch_up(d, 0);            // never stack two fingers on one slot
    int rx = map_axis(x, d->scr_w, d->xmin, d->xmax);
    int ry = map_axis(y, d->scr_h, d->ymin, d->ymax);
    d->trkid = d->trk_max > 0 ? (d->trkid + 1) % (d->trk_max + 1) : (d->trkid + 1) & 0x7fff;
    struct frame f;
    build_contact(d, &f, rx, ry, 1);
    flush(d, &f);
    d->touching = 1;
    d->last_rx = rx;
    d->last_ry = ry;
}

static void touch_move(struct touch_dev *d, int x, int y)
{
    if (!d->touching) { touch_down(d, x, y); return; }
    int rx = map_axis(x, d->scr_w, d->xmin, d->xmax);
    int ry = map_axis(y, d->scr_h, d->ymin, d->ymax);
    if (rx == d->last_rx && ry == d->last_ry) return;
    struct frame f;
    build_contact(d, &f, rx, ry, 0);
    flush(d, &f);
    d->last_rx = rx;
    d->last_ry = ry;
}

static void do_tap(struct touch_dev *d, int x, int y, int hold_ms)
{
    if (hold_ms < 1) hold_ms = 12;
    if (hold_ms > 1000) hold_ms = 1000;
    touch_down(d, x, y);
    sleep_until(now_ns() + hold_ms * 1000000LL);   // DOWN lands instantly; this is only the finger-on-glass time
    touch_up(d, 0);
}

static void do_swipe(struct touch_dev *d, int x1, int y1, int x2, int y2, int dur_ms)
{
    if (dur_ms < 16) dur_ms = 16;
    if (dur_ms > 3000) dur_ms = 3000;
    int steps = dur_ms / STEP_MS;
    if (steps < 2) steps = 2;
    long long t0 = now_ns(), span = dur_ms * 1000000LL;
    touch_down(d, x1, y1);
    for (int i = 1; i <= steps && !g_quit; i++) {
        sleep_until(t0 + span * i / steps);        // absolute deadlines: no drift
        touch_move(d, x1 + (int)((long long)(x2 - x1) * i / steps),
                      y1 + (int)((long long)(y2 - y1) * i / steps));
    }
    touch_up(d, 0);
}

static void handle_pkt(struct touch_dev *d, const struct evi_pkt *p)
{
    switch (p->op) {
    case OP_DOWN:   touch_down(d, p->a, p->b); break;
    case OP_MOVE:   touch_move(d, p->a, p->b); break;
    case OP_UP:     touch_up(d, 0); break;
    case OP_TAP:    do_tap(d, p->a, p->b, p->c); break;
    case OP_SWIPE:  do_swipe(d, p->a, p->b, p->c, p->d, p->e); break;
    case OP_RESET:  touch_up(d, 1); break;
    case OP_SCREEN: if (p->a > 1 && p->b > 1) { d->scr_w = p->a; d->scr_h = p->b; } break;
    case OP_QUIT:   g_quit = 1; break;
    default:        break;                          // PING / unknown: just a wake-up
    }
}

// ----------------------------------------------------------------- daemon ---

static int run_daemon(struct touch_dev *d)
{
    printf("evi-ok node=%s name=\"%s\" proto=%s x=%d..%d y=%d..%d slots=%d pressure=%d\n",
           d->path, d->name, proto_name(d->proto), d->xmin, d->xmax, d->ymin, d->ymax,
           d->nslots, d->has_pressure ? d->pressure : 0);
    fflush(stdout);

    prctl(PR_SET_NAME, "mochibun-evi", 0, 0, 0);

    struct sigaction sa;
    memset(&sa, 0, sizeof sa);
    sa.sa_handler = on_signal;                       // no SA_RESTART: we want poll() to wake up
    sigaction(SIGTERM, &sa, NULL);
    sigaction(SIGINT, &sa, NULL);
    signal(SIGHUP, SIG_IGN);
    signal(SIGPIPE, SIG_IGN);

    unsigned char buf[sizeof(struct evi_pkt) * 32];
    size_t have = 0;

    while (!g_quit) {
        struct pollfd pfd = { .fd = 0, .events = POLLIN };
        int timeout = d->touching ? TOUCH_WATCHDOG_MS : -1;
        int r = poll(&pfd, 1, timeout);
        if (r < 0) { if (errno == EINTR) continue; break; }
        if (r == 0) {
            if (d->touching) touch_up(d, 0);         // app disappeared mid-drag: let go
            continue;
        }
        ssize_t n = read(0, buf + have, sizeof buf - have);
        if (n <= 0) {
            if (n < 0 && (errno == EINTR || errno == EAGAIN)) continue;
            break;                                   // EOF: pipe closed / app exited
        }
        have += (size_t)n;

        size_t off = 0;
        while (have - off >= sizeof(struct evi_pkt) && !g_quit) {
            struct evi_pkt p;
            memcpy(&p, buf + off, sizeof p);
            if (p.magic != EVI_MAGIC) { off++; continue; }          // resync a byte at a time
            off += sizeof p;
            handle_pkt(d, &p);
        }
        memmove(buf, buf + off, have - off);
        have -= off;
    }
    touch_up(d, 0);
    return 0;
}

// ------------------------------------------------------------------- main ---

static int usage(void)
{
    fprintf(stderr, "usage: evinject info | tap X Y [ms] | swipe X1 Y1 X2 Y2 [ms] | daemon --stdin\n"
                    "       common: [--screen WxH] [--device PATH] [--slot N]\n");
    return 64;
}

int main(int argc, char **argv)
{
    if (argc < 2) return usage();
    const char *mode = argv[1];
    const char *dev_override = NULL;
    int scr_w = 480, scr_h = 800, slot = 0, npos = 0, pos[8] = {0};
    int use_stdin = 0;

    for (int i = 2; i < argc; i++) {
        const char *a = argv[i];
        int has_val = i + 1 < argc;
        if (!strcmp(a, "--device") && has_val)        dev_override = argv[++i];
        else if (!strcmp(a, "--stdin"))               use_stdin = 1;
        else if (!strcmp(a, "--slot") && has_val)     slot = atoi(argv[++i]);
        else if (!strcmp(a, "--screen") && has_val) {
            if (sscanf(argv[++i], "%dx%d", &scr_w, &scr_h) != 2) return usage();
        } else if (strncmp(a, "--", 2) != 0 && npos < 8) pos[npos++] = atoi(a);
        else return usage();
    }

    struct touch_dev d;
    memset(&d, 0, sizeof d);
    int is_info = !strcmp(mode, "info");
    if (pick_device(&d, dev_override, is_info) != 0) {
        fprintf(stderr, "evi-err no-touch-node (are we root? try: evinject info)\n");
        return 10;
    }
    if (is_info) { printf("picked: "); print_dev(&d); return 0; }

    d.scr_w = scr_w > 1 ? scr_w : 480;
    d.scr_h = scr_h > 1 ? scr_h : 800;
    d.slot = d.proto == PROTO_B ? (slot < 0 ? 0 : slot >= d.nslots ? d.nslots - 1 : slot) : 0;

    struct sigaction sa;
    memset(&sa, 0, sizeof sa);
    sa.sa_handler = on_signal;
    sigaction(SIGTERM, &sa, NULL);
    sigaction(SIGINT, &sa, NULL);

    if (!strcmp(mode, "tap") && npos >= 2) {
        do_tap(&d, pos[0], pos[1], npos >= 3 ? pos[2] : 20);
        return 0;
    }
    if (!strcmp(mode, "swipe") && npos >= 4) {
        do_swipe(&d, pos[0], pos[1], pos[2], pos[3], npos >= 5 ? pos[4] : 300);
        return 0;
    }
    if (!strcmp(mode, "daemon")) {
        if (!use_stdin) return usage();
        return run_daemon(&d);
    }
    return usage();
}
