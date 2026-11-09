# Exhaustive Engineering Handover: Chinoe AT-M120 Exterior Lid LED Subsystem (lidled)

## 1. Project Overview & Target Environment
- Project: Mochibun (com.crinklecodone.mochibun), an open-source personalized launcher and keitai companion app for Android flip phones.
- Repository Location: /home/emii/Projects/Mochibun (Branch: main).
- Target Device: Chinoe AT-M120 flip phone (Japanese style clamshell).
  - OS: Android 12 (API level 31).
  - SoC: MediaTek MT6761 (Helio A22) quad-core 32-bit (armv7l userspace and kernel).
  - ADB Serial: B00801244M037239 (device is permanently rooted with Magisk su).
- Development Toolchain:
  - Host OS: Linux x86_64.
  - Android NDK Clang Path:
    /home/emii/Android/Sdk/ndk/28.2.13676358/toolchains/llvm/prebuilt/linux-x86_64/bin/armv7a-linux-androideabi31-clang
  - Direct Standalone Compilation Command:
    /home/emii/Android/Sdk/ndk/28.2.13676358/toolchains/llvm/prebuilt/linux-x86_64/bin/armv7a-linux-androideabi31-clang -O2 -Wall -Wextra -fPIE -pie /home/emii/Projects/Mochibun/app/src/main/cpp/lidled/lidled.c -o /tmp/lidled
  - Device Test Location: /data/local/tmp/lidled
  - Mochibun Packaging: Packaged as liblidled.so inside APK lib/armeabi-v7a/, dynamically extracted at runtime by LidLedManager.kt.

---

## 2. Hardware Architecture & The Awinic AW22127 Controller
The exterior clamshell of the AT-M120 features 5 circular transparent windows in a vertical row with full-color RGB illumination.

### A. Controller Specifications
- IC: Awinic AW22127 (AW22127QNR) intelligent LED matrix driver SoC.
- I2C Bus: /dev/i2c-5 at 7-bit slave address 0x6a (/sys/devices/platform/11016000.i2c5/i2c-5/5-006a/leds/aw22xxx_led).
- Topology: 3 multiplexed high-side common lines (COM1-COM3) x 9 low-side constant-current sink lines (LED1-LED9), providing a 3 x 9 matrix capable of driving 27 monochrome LEDs or 9 RGB channels.
- Registers: Paged register map.
  - 0xff is the page selector: write "0xff 0x00" selects Page 0; write "0xff 0x01" selects Page 1.
  - Page 0 handles chip reset, global clocks, power enables, and display state.
  - Page 1 contains LED enable bitmasks (0x01-0x0a), COM/LED scan configuration (0x05, 0x06), and 27 individual PWM brightness registers (0x10 through 0x2a).

### B. Strict Sysfs Constraints (CRITICAL)
The vendor kernel driver exposes several sysfs nodes in /sys/class/leds/aw22xxx_led/:
- brightness, effect, cfg, fw, rgb, task0, task1
- case_call, case_message, case_notification, case_clock, case_charge, case_continue_charge, case_lowpower, case_turnonoff, case_pm25, case_hotkey, case_factory_test

DO NOT WRITE TO ANY OF THESE SYSFS NODES.
- Writing to brightness, effect, or any case_* node kicks off kernel background workqueues (ledcase_call_wq, ledcase_notific, ledcase_message, aw22xxx_continue_charge_work_routine).
- These workqueues load hard-coded vendor rainbow breathing microcode firmware (.bin files) from /vendor/firmware directly into chip SRAM and loop forever, ignoring custom RGB colors and locking out user control.
- Mochibun's Architecture: All control is achieved by writing raw register pairs strictly to /sys/class/leds/aw22xxx_led/reg using the format "0x%02x 0x%02x\n". This bypasses the kernel driver's state machine completely.

---

## 3. Verified Physical Window-to-Channel Mapping
Through live single-register isolation testing on the device with the clamshell closed, the 5 physical lid windows were mapped to the controller's 9 RGB channel groups:

- Window 1 (Top): Channel 0 (0x10=R, 0x11=G, 0x12=B)
- Window 2: Channel 1 (0x13=R, 0x14=G, 0x15=B)
- Window 3 (Center): Channel 3 (0x19=R, 0x1a=G, 0x1b=B)
- Window 4: Channel 4 (0x1c=R, 0x1d=G, 0x1e=B)
- Window 5 (Bottom): Channel 6 (0x22=R, 0x23=G, 0x24=B)

(Note: Channels 2, 5, 7, and 8 are unused or multiplexed on the AT-M120 PCB layout).

Bitmasks for the 9-Channel Controller:
- MASK_10101 (Windows 1, 3, 5): ((1u << 0) | (1u << 3) | (1u << 6))
- MASK_01010 (Windows 2, 4): ((1u << 1) | (1u << 4))
- MASK_ALL (All 5 Windows): (MASK_10101 | MASK_01010)

---

## 4. The Working Register Programming Sequence
Extracted from vendor firmware disassembly and confirmed live on hardware:

### Step 1: Page 0 Wake & Setup
write_reg(fd, 0xff, 0x00); // Select Page 0
write_reg(fd, 0x05, 0x00); // Standby / clear display mode
write_reg(fd, 0x04, 0x01); // Global chip power enable
write_reg(fd, 0x02, 0x00); // Oscillator reset
write_reg(fd, 0x02, 0x01); // Internal oscillator enable
write_reg(fd, 0x04, 0x01); // Re-assert enable
write_reg(fd, 0x0c, 0x00); // Clear IRQ flags

### Step 2: Page 1 Configuration & Channel Masking
write_reg(fd, 0xff, 0x01); // Select Page 1
write_reg(fd, 0x00, 0x00); // Base config
write_reg(fd, 0x01, 0x00);
write_reg(fd, 0x02, 0x00);
write_reg(fd, 0x03, 0x00);
write_reg(fd, 0x04, 0x00);
write_reg(fd, 0x05, 0x03); // COM scan mode: 3 Common lines (COM1-COM3)
write_reg(fd, 0x06, 0x09); // LED sink mode: 9 Sink lines (LED1-LED9)
write_reg(fd, 0x0b, 0x00);

### Step 3: Write 24-bit RGB PWM Values to Channels 0..8
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

### Step 4: Latch & Display Commit
write_reg(fd, 0x2b, 0x00);
write_reg(fd, 0x2c, 0xff); // PWM Data Update / Latch strobe
write_reg(fd, 0xff, 0x00); // Back to Page 0
write_reg(fd, 0x05, 0x02); // Enable display engine (Manual PWM mode)
write_reg(fd, 0x04, 0x03); // Enable matrix scanning and output current drivers

### Step 5: Clean Hardware Shutoff (write_off)
write_reg(fd, 0xff, 0x00);
write_reg(fd, 0x05, 0x00); // Disable display engine
write_reg(fd, 0x04, 0x00); // Disable chip outputs
write_reg(fd, 0x02, 0x00); // Disable clock
write_reg(fd, 0x01, 0x55); // Software reset
write_reg(fd, 0xff, 0x01);
for (int r = 0x10; r <= 0x2a; r++) {
    write_reg(fd, r, 0x00); // Zero all PWM registers
}
write_reg(fd, 0xff, 0x00);
write_reg(fd, 0x05, 0x00);
write_reg(fd, 0x04, 0x00);

---

## 5. What Was Verified & The Breakthrough Discoveries

### Discovery 1: Why Shimmer Works With Zero Hiccups on Battery
- In our live testing on the physical device, when the phone was unplugged from USB:
  su -c '/data/local/tmp/lidled shimmer ff1493 0.3 8 off -f'
  worked with 100% perfection!
- The 5 windows alternated smoothly between 1 0 1 0 1 and 0 1 0 1 0 in Sakura Pink (ff1493), and lidled off shut the LEDs down completely dark with no trailing vendor animations.
- When the phone was plugged into USB, the kernel battery driver (aw22xxx_continue_charge_work_routine) was firing periodically to show the green charging LED on Window 1, creating a minor conflict. On battery (real-world daily use), that routine is idle and lidled has full, exclusive control.

### Discovery 2: Why shimmer Works but One-Shot pattern and solid Go Dark
- Notice the structural difference between shimmer and pattern/solid in app/src/main/cpp/lidled/lidled.c:
  - shimmer: Either runs in the foreground or forks a background daemon (PID tracked in /data/local/tmp/lidled.pid). Crucially, it keeps the file descriptor /sys/class/leds/aw22xxx_led/reg open for its entire lifetime.
  - pattern and solid: They open the file descriptor, execute write_channel_pattern(...) once, immediately call close(reg_fd), and exit (exit(0)).
- When the one-shot process exits and closes the file descriptor:
  - Either the Linux kernel LED class driver resets the I2C chip back to idle on close(), OR Android's userspace lights service reclaims the LED state once the writing process exits.
  - When the user spammed lidled pattern ... in a shell loop, the LEDs stayed lit!

---

## 6. Current Implementation Details

### A. Mochibun Kotlin Layer (LidLedManager.kt)
Located at: app/src/main/java/com/crinklecodone/mochibun/core/system/LidLedManager.kt
- Implements a priority ladder for the closed clamshell:
  Incoming Call > Unread Notifications > Charging > Off
- Manages battery conservation: completely disables LEDs if battery < 5% or lid is open.
- Commands built:
  - Off: '$helperPath' off
  - Solid: '$helperPath' pattern 11111 $hexColor
  - Pattern: '$helperPath' pattern $channelsMask $hexColor
  - Shimmer: '$helperPath' shimmer $hexColor $frameSeconds $cycles $settleMode
    - Notification shimmer: 0.8s per frame, 6 cycles, settles on 10101 (steady glyph).
    - Call shimmer: 0.4s per frame, 56 cycles, settles on solid.
- Extraction logic: Automatically extracts liblidled.so from APK assets to app private storage, or uses /data/local/tmp/lidled if present during development.

### B. Mochibun Unit Tests (LidLedManagerTest.kt)
Located at: app/src/test/java/com/crinklecodone/mochibun/LidLedManagerTest.kt
- All 18 unit tests for LidLedManager pass.
- Full test suite passes: ./gradlew :app:testDebugUnitTest (153 passed, 0 failed).

### C. Native C Helper (lidled.c)
Located at: app/src/main/cpp/lidled/lidled.c
- Compiled via CMake into liblidled.so (set as an executable PIE ELF).
- Handles mutual exclusion via flock on /data/local/tmp/lidled.lock.
- Tracks background shimmer daemon PID in /data/local/tmp/lidled.pid and kills prior instances on any new command invocation.

---

## 7. The Concrete Next Steps to Perfect solid and pattern

To make solid and pattern as solid and persistent as shimmer:
1. Background Daemon for Static Commands:
   In lidled.c, instead of performing a single write and calling close(reg_fd); exit(0);, static commands (solid and pattern) should:
   - Call stop_previous_shimmer() to stop any running daemon.
   - Fork into a background daemon (the same way shimmer does when not passed -f), writing the PID to /data/local/tmp/lidled.pid.
   - Write the requested pattern to /reg.
   - Hold the file descriptor open and pause in a signal-interruptible loop (while (!g_stop) pause(); or nanosleep).
   - When any subsequent command is called (lidled off, lidled shimmer, or another solid/pattern), stop_previous_shimmer() will send SIGTERM, cleanly terminating the prior background hold and taking over.
2. Recompile and Push:
   Compile with NDK clang and push to /data/local/tmp/lidled:
   /home/emii/Android/Sdk/ndk/28.2.13676358/toolchains/llvm/prebuilt/linux-x86_64/bin/armv7a-linux-androideabi31-clang -O2 -Wall -Wextra -fPIE -pie /home/emii/Projects/Mochibun/app/src/main/cpp/lidled/lidled.c -o /tmp/lidled
   adb -s B00801244M037239 push /tmp/lidled /data/local/tmp/lidled
   adb -s B00801244M037239 shell "chmod +x /data/local/tmp/lidled"
3. Verify on Device:
   Test with the user:
   - su -c '/data/local/tmp/lidled solid ff1493' -> All 5 windows stay steadily lit.
   - su -c '/data/local/tmp/lidled pattern 10101 ff1493' -> Windows 1, 3, 5 stay steadily lit.
   - su -c '/data/local/tmp/lidled off' -> LEDs shut off immediately.
4. Run Verification Suite:
   Run ./gradlew :app:testDebugUnitTest :app:assembleDebug to ensure all 153 unit tests pass and the APK builds cleanly.
