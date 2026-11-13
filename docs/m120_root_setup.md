### 🔓 Advanced Setup: Rooting (Magisk), Performance Tuning & microG

Mochibun works great as a regular launcher right out of the box! (˶ᵔ ᵕ ᵔ˶) But if you root your AT-M120, it unlocks a lot more: key remapping, a virtual cursor, custom lid LED colors, a recents provider, and SystemUI theming.

---

#### 1. Obtaining Stock `boot.img` Directly from Device (MTKClient)

The stock `boot.img` contains proprietary OEM firmware, so we can't host it or ship it in this repo. That's fine, because you can pull your own phone's boot image straight over USB with `mtkclient` in MediaTek BootROM (BROM) mode:

##### Step A: Install MTKClient on PC
```bash
# Clone and install dependencies (Linux / macOS / WSL)
git clone https://github.com/bkerler/mtkclient.git
cd mtkclient
pip3 install -r requirements.txt

# (Linux only) Setup udev rules for MediaTek USB VCOM
sudo usermod -aG dialout $USER
sudo cp setup/51-edl.rules /etc/udev/rules.d/
sudo udevadm control --reload-rules
sudo udevadm trigger
```

##### Step B: Dump the Boot Partition
1. Fully power off your AT-M120 and unplug it.
2. On your PC, start the read command:
   ```bash
   python3 mtk r boot boot.img
   ```
3. Hold **Volume Down** (or both volume keys) and plug the phone into your computer.
4. `mtkclient` talks to the MT6761's BROM, saves the active `boot.img` to your current folder, and exits. Unplug and power the phone back on.

---

#### 2. Unlocking the Bootloader & Installing Magisk

> ⚠️ **Warning**: Unlocking the bootloader wipes all your user data (`/data`). Back up anything you care about first.

1. **Enable Developer Options & OEM Unlocking**:
   - Go to **Settings > System > About phone**.
   - Tap **Build number** 7 times to turn on Developer options.
   - Head to **Settings > System > Developer options** and turn on:
     - **OEM unlocking**
     - **USB debugging**

2. **Unlock the Bootloader**:
   - Plug the AT-M120 into your PC.
   - Reboot into fastboot:
     ```bash
     adb reboot bootloader
     ```
   - Unlock it:
     ```bash
     fastboot flashing unlock
     ```
   - The phone will ask you to confirm. Use the volume keys to accept the wipe and unlock, then boot back into Android:
     ```bash
     fastboot reboot
     ```

3. **Patch `boot.img` with Magisk**:
   - Copy your dumped `boot.img` to the phone:
     ```bash
     adb push boot.img /sdcard/Download/
     ```
   - Install the latest [Magisk APK](https://github.com/topjohnwu/Magisk/releases):
     ```bash
     adb install Magisk-vXX.X.apk
     ```
   - Open Magisk, tap **Install > Select and Patch a File**, and pick `/sdcard/Download/boot.img`.
   - Pull the patched image back to your PC:
     ```bash
     adb pull /sdcard/Download/magisk_patched_*.img ./magisk_boot.img
     ```

4. **Flash Patched Boot Image**:
   - Reboot into fastboot again:
     ```bash
     adb reboot bootloader
     ```
   - Flash it to the active boot partition:
     ```bash
     fastboot flash boot magisk_boot.img
     fastboot reboot
     ```
   - Open the Magisk app and let it finish its first-time setup.

---

#### 3. Installing microG (Replacing Heavy Google Play Services)

The MT6761 only has about 1.8 GB of RAM. Swapping Google Play Services (GMS) for microG frees up a lot of memory and keeps the Low Memory Killer (LMK) from closing your apps in the background.

Android 12 on the AT-M120 uses a read-only shared system partition (EROFS/ext4), so don't try to write to `/system` directly.

1. **Enable Zygisk**:
   - Open Magisk > Settings ⚙️ and turn **Zygisk** on.
   - Reboot.

2. **Enable Signature Spoofing**:
   - Flash **FakeGApps** or **LSPosed (Zygisk release)** from Magisk Modules.
   - Going the LSPosed route? Install and enable the **FakeGApps** module there to get signature spoofing.

3. **Install microG Components**:
   - Grab the latest microG builds from [microg.org](https://microg.org/download.html):
     - `Services Core` (GmsCore)
     - `Services Framework Proxy` (GsfProxy)
   - Install both over ADB:
     ```bash
     adb install com.google.android.gms-*.apk
     adb install com.google.android.gsf-*.apk
     ```
   - Open **microG Settings** on the phone and run **Self-Check**. Every spoofing and permission box should be ticked.

4. **Install Aurora Store**:
   - Install Aurora Store (ARMv7 32-bit / universal APK):
     ```bash
     adb install -r AuroraStore.apk
     ```
   - With Aurora Store and microG together, you can install Play Store apps without running Google's official services.

---

#### 4. Installing Vector and Zygisk Next (LSPosed)
Mochibun uses Xposed hooks (`android` and `com.android.systemui`) to intercept physical keys globally (including the F4 cursor toggle, LSK/RSK soft keys, and custom key remaps) and apply pastel theming across SystemUI—without needing any Android Accessibility Service.

> ⚠️ **Important:** Do **not** install both original `zygisk_lsposed` and `zygisk_vector` at the same time—running two Xposed frameworks concurrently causes `system_server` crashes on the AT-M120. Use **Zygisk Next + Vector** only.

1. **Disable Magisk's Built-In Zygisk**:
   - Open **Magisk → Settings ⚙️** and make sure built-in **Zygisk** is toggled **OFF** (Zygisk Next provides its own Zygisk runtime engine).
2. **Download & Flash Both Modules**:
   - Download the latest release ZIPs on your PC or phone:
     - **Zygisk Next**: [Dr-TSNG/ZygiskNext Releases](https://github.com/Dr-TSNG/ZygiskNext/releases)
     - **Vector (LSPosed fork)**: [JingMatrix/Vector Releases](https://github.com/JingMatrix/Vector/releases) *(grab the `zygisk` release)*
   - Open **Magisk → Modules → Install from storage**, flash **Zygisk Next**, then flash **Vector**, and **reboot** the phone.
3. **Activate Mochibun in Vector Manager**:
   - Open the **Vector** (LSPosed) app from your app drawer (or status bar notification) → **Modules** → tap **Mochibun** and toggle it **ON**.
   - Check **both** target scopes:
     - **System Framework** (`android`)
     - **System UI** (`com.android.systemui`)
   - **Reboot** once more so `system_server` and SystemUI load Mochibun's hooks.

---

#### 5. Installing QuickSwitch
QuickSwitch allows Mochibun to take over as the system recents provider (so that the existing Launcher3 / Simple Mode launchers can be disabled safely).

1. **Download / Install QuickSwitch Magisk Module**:
   - Download the latest release ZIP from [skittles9823/QuickSwitch Releases](https://github.com/skittles9823/QuickSwitch/releases).
   - Open **Magisk → Modules → Install from storage**, select the QuickSwitch ZIP, and **reboot** the phone.
2. **Bind Mochibun as your system Quickstep / Recents provider**:
   The QuickSwitch GUI app often fails to elevate root on Android 12, so run the CLI backend directly via ADB (`adb shell su`):
   ```bash
   su -c "/data/adb/modules/quickswitch/quickswitch --ch=one.crinklecod.mochibun"
   ```

   > **Magisk CLI Fix:** Current builds of `skittles9823/QuickSwitch` only resolve `APKPATH` when `$KSU` or `$APATCH` is set, causing `cp: Needs 2 arguments` / `error: No dump apk specified` on Magisk. Patch the script once before running `--ch`:
   > ```bash
   > su -c "sed -i 's/\$KSU/1/' /data/adb/modules/quickswitch/quickswitch"
   > ```
   > Then re-run `--ch=one.crinklecod.mochibun` and **reboot**.

3. **Disable stock software**:
   > ⚠️ **Warning:** Only disable stock launchers **after** rebooting and confirming Mochibun functions as both your Home app and your Recents provider!

   Disable stock Launcher3:
   ```bash
   adb shell pm disable-user --user 0 com.android.launcher3
   ```
   Disable stock Simple Mode (a basic "dumbphone"-like UI) that android will default to w/o Launcher3:
   ```bash
   adb shell pm disable-user --user 0 com.hnlens.simplemode
   ```

#### 6. Essential Magisk Boot Tuning for Mochibun

This boot script keeps animations snappy and stops Android from killing Mochibun when memory gets tight:

1. Create `/data/adb/service.d/01_m120_tune.sh`:
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
   PID=\$(pidof one.crinklecod.mochibun)
   if [ -z \"\$PID\" ]; then
       PID=\$(pidof com.crinklecodone.mochibun)
   fi

   if [ -n \"\$PID\" ]; then
       echo -1000 > /proc/\$PID/oom_score_adj
   fi
   EOF"
   ```

2. Make it executable:
   ```bash
   adb shell su -c "chmod 755 /data/adb/service.d/01_m120_tune.sh"
   ```

---

#### 7. Post-Root Feature Checklist for Mochibun

Now that you're rooted, check and turn on these features in Mochibun:
* **Grant Root**: Allow Magisk superuser access when Mochibun asks. That happens when you open it or start the virtual cursor.
* **Quick Settings Toggles**: Turn on direct root toggles in Mochibun's settings. Wi-Fi and Bluetooth then flip right on the Widget deck, with no stock Android dialogs in the way.
* **Enable the LSPosed Module**: In LSPosed/Vector, turn on the **Mochibun** module with the *System Framework* and *System UI* scopes, then reboot. That wakes up system-wide keys, the virtual cursor, Media Volume Lock, and SystemUI theming.
* **Lid LED Notifications**: Make sure notification pulses on the outer lid are going through `/sys/class/leds/aw22xxx_led/` with root.
