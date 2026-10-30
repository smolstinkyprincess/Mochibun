### 🔓 Advanced Setup: Rooting (Magisk), Performance Tuning & microG

While Mochibun runs out of the box as a standard launcher, rooting your AT-M120 unlocks its full capability: instantaneous in-place Quick Settings toggles, zero-latency virtual cursor clicking, custom lid LED patterns, and automated RAM management[cite: 2, 11, 20].

---

#### 1. Obtaining Stock `boot.img` Directly from Device (MTKClient)

Because the stock `boot.img` contains proprietary OEM binary firmware, it cannot be hosted or bundled in this repository. You can dump your phone's exact boot image directly over USB using `mtkclient` in MediaTek BootROM (BROM) mode[cite: 2, 7]:

##### Step A: Install MTKClient on PC
```bash
# Clone and install dependencies (Linux / macOS / WSL)
git clone [https://github.com/bkerler/mtkclient.git](https://github.com/bkerler/mtkclient.git)
cd mtkclient
pip3 install -r requirements.txt

# (Linux only) Setup udev rules for MediaTek USB VCOM
sudo usermod -aG dialout $USER
sudo cp setup/51-edl.rules /etc/udev/rules.d/
sudo udevadm control --reload-rules
sudo udevadm trigger
```

##### Step B: Dump the Boot Partition
1. Power off your AT-M120 completely and unplug it from USB[cite: 4, 14].
2. Start the read command on your PC:
   ```bash
   python3 mtk r boot boot.img
   ```
3. Hold **Volume Down** (or both volume keys) on the physical keypad and plug the phone into the computer via USB.
4. `mtkclient` will handshake with the MediaTek MT6761 BROM, dump the active `boot.img` to your current directory, and exit cleanly. Unplug and power on the device[cite: 1, 7].

---

#### 2. Unlocking the Bootloader & Installing Magisk

> ⚠️ **Warning**: Unlocking the bootloader will wipe all user data (`/data`). Back up your files before proceeding[cite: 2].

1. **Enable Developer Options & OEM Unlocking**:
   - Navigate to **Settings > System > About phone**.
   - Tap **Build number** 7 times to enable Developer options.
   - Go to **Settings > System > Developer options** and enable:
     - **OEM unlocking**
     - **USB debugging**

2. **Unlock the Bootloader**:
   - Connect the AT-M120 to your PC via USB.
   - Reboot into fastboot:
     ```bash
     adb reboot bootloader
     ```
   - Unlock the device:
     ```bash
     fastboot flashing unlock
     ```
   - Follow the on-screen prompt on the phone (using the physical volume keys) to confirm the wipe and unlock. Reboot to Android:
     ```bash
     fastboot reboot
     ```

3. **Patch `boot.img` with Magisk**:
   - Push your dumped `boot.img` to the phone:
     ```bash
     adb push boot.img /sdcard/Download/
     ```
   - Install the latest [Magisk APK](https://github.com/topjohnwu/Magisk/releases) on the device:
     ```bash
     adb install Magisk-vXX.X.apk
     ```
   - Open Magisk, tap **Install > Select and Patch a File**, and choose `/sdcard/Download/boot.img`.
   - Pull the patched image back to your PC:
     ```bash
     adb pull /sdcard/Download/magisk_patched_*.img ./magisk_boot.img
     ```

4. **Flash Patched Boot Image**:
   - Reboot into fastboot:
     ```bash
     adb reboot bootloader
     ```
   - Flash to the active boot partition:
     ```bash
     fastboot flash boot magisk_boot.img
     fastboot reboot
     ```
   - Open the Magisk app to complete the initial setup environment.

---

#### 3. Installing microG (Replacing Heavy Google Play Services)

The MediaTek MT6761 has ~1.8 GB RAM[cite: 1, 2]. Replacing standard Google Play Services (GMS) with microG drastically cuts memory usage and prevents background Low Memory Killer (LMK) app closures[cite: 1, 2].

Because Android 12 on the AT-M120 uses a read-only shared system partition (EROFS/ext4)[cite: 1, 10], do not attempt to write directly to `/system`.

1. **Enable Zygisk**:
   - Open Magisk > Settings ⚙️ > toggle **Zygisk** ON.
   - Reboot the phone.

2. **Enable Signature Spoofing**:
   - Flash **FakeGApps** or **LSPosed (Zygisk release)** via Magisk Modules.
   - If using LSPosed, install and enable the **FakeGApps** module to grant signature spoofing permissions.

3. **Install microG Components**:
   - Download the latest microG builds from [microg.org](https://microg.org/download.html):
     - `Services Core` (GmsCore)
     - `Services Framework Proxy` (GsfProxy)
   - Install both via ADB:
     ```bash
     adb install com.google.android.gms-*.apk
     adb install com.google.android.gsf-*.apk
     ```
   - Open **microG Settings** on the phone and run the **Self-Check** checklist to ensure all spoofing and permission checkmarks are active.

4. **Install Aurora Store**:
   - Install Aurora Store (ARMv7 32-bit / universal APK):
     ```bash
     adb install -r AuroraStore.apk
     ```
   - Use Aurora Store with microG for Play Store app installation without running official Google Mobile Services[cite: 4].

---

#### 4. Essential Magisk Boot Tuning for Mochibun

To prevent frame drops and keep Mochibun from being killed under memory pressure, install Mochibun's automated boot tuning script[cite: 2, 7]:

1. Create `/data/adb/service.d/01_m120_tune.sh`[cite: 2, 8]:
   ```bash
   adb shell su -c "cat << 'EOF' > /data/adb/service.d/01_m120_tune.sh
   #!/system/bin/sh

   # Wait until boot finishes
   until [ \"\$(getprop sys.boot_completed)\" = \"1\" ]; do
       sleep 2
   done

   # Disable window animations for zero-latency D-pad navigation
   settings put global window_animation_scale 0.0
   settings put global transition_animation_scale 0.0
   settings put global animator_duration_scale 0.0

   # Virtual memory tuning
   if [ -w /proc/sys/vm/swappiness ]; then
       echo 60 > /proc/sys/vm/swappiness
   fi
   if [ -w /proc/sys/vm/dirty_background_ratio ]; then
       echo 5 > /proc/sys/vm/dirty_background_ratio
   fi
   if [ -w /proc/sys/vm/dirty_ratio ]; then
       echo 15 > /proc/sys/vm/dirty_ratio
   fi

   # Expand ZRAM to ~1.75 GB
   if [ -e /dev/block/zram0 ]; then
       swapoff /dev/block/zram0 2>/dev/null
       echo 1 > /sys/block/zram0/reset
       echo 1792M > /sys/block/zram0/disksize
       mkswap /dev/block/zram0 >/dev/null 2>&1
       swapon /dev/block/zram0
   fi

   # Protect Mochibun from LMKD termination
   PID=\$(pidof com.crinklecodone.mochibun)
   if [ -z \"\$PID\" ]; then
       PID=\$(pidof com.tnyemii.mochibun)
   fi

   if [ -n \"\$PID\" ]; then
       echo -1000 > /proc/\$PID/oom_score_adj
   fi
   EOF"
   ```

2. Make the script executable[cite: 8, 15]:
   ```bash
   adb shell su -c "chmod 755 /data/adb/service.d/01_m120_tune.sh"
   ```

---

#### 5. Post-Root Feature Checklist for Mochibun

Once rooted, verify and enable these features in Mochibun:
* **Grant Root**: When opening Mochibun or triggering the virtual cursor, allow Magisk superuser access[cite: 2, 13].
* **Quick Settings Toggles**: Turn on direct root toggles in Mochibun settings so Wi-Fi and Bluetooth toggle instantly on the Widget deck without opening stock Android dialogs[cite: 2, 11].
* **Lid LED Notifications**: Ensure exterior notification pulses hook directly into `/sys/class/leds/aw22xxx_led/` via root[cite: 13, 20].
* **Disable Stock Simple Mode**:
  ```bash
  adb shell pm disable-user --user 0 com.hnlens.simplemode
  ```