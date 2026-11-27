# MicroMochi BLE Protocol (v1)

Wire protocol between Mochibun's opt-in **MicroMochi Keychain** BLE GATT server
(`pet/PetBleCompanionServer.kt`, constants in `pet/PetBleProtocol.kt`) and the ESP32-S3 e-paper
keychain firmware. Everything is local Bluetooth Low Energy: no Wi-Fi, no internet, no pairing app.

Enable it on the phone in **Settings → Permissions & Root → MicroMochi Keychain (BLE)**. On Android 12+
this asks for the *Nearby devices* permission. The server only runs while the switch is on and
Bluetooth is enabled.

## Discovery

| Field | Value |
| :--- | :--- |
| Primary service UUID | `1281dbd0-0fd6-4734-99b5-8f45f5403402` |
| Advertising packet | Flags + complete 128-bit service UUID list (the service UUID above) |
| Scan response | Service data under the same UUID: ASCII `Mochibun` (8 bytes) |
| Mode | Connectable, `ADVERTISE_MODE_LOW_POWER` (~1 s interval), low TX power |

Filter on the **service UUID**. Android can only advertise the phone's own Bluetooth name (changing
it would rename the whole phone), so the GAP device name is *not* `Mochibun`.

Advertising **pauses while a keychain is subscribed** to State notifications and resumes when it
disconnects or unsubscribes.

## Security

Every attribute requires an **encrypted, bonded link**. The first read, write, or CCCD write
gets *Insufficient Encryption*. The firmware must then pair (Just Works / no IO capability is
fine) and store the bond. Android shows a one-time pairing prompt. With NimBLE-Arduino, for example:

```cpp
NimBLEDevice::setSecurityAuth(/*bonding*/ true, /*mitm*/ false, /*sc*/ true);
NimBLEDevice::setSecurityIOCap(BLE_HS_IO_NO_INPUT_OUTPUT);
```

Writes without response sent before encryption is up are silently dropped by the phone.

## MTU

Request an ATT MTU of **247** right after connecting. Notifications must fit in `MTU - 3` bytes; the
phone trims the dialogue line so the JSON fits, and **skips** a notification entirely when even the
fixed fields don't fit (default MTU 23). Reads always work at any MTU (long reads).

## Characteristics

### State: `1281dbd1-0fd6-4734-99b5-8f45f5403402`
Properties `READ | NOTIFY`, CCCD `0x2902`.

- **Read** applies decay up to now (`PetManager.refresh()`) and returns the current state.
- **Notify** after writing `01 00` to the CCCD:
  - the current state arrives immediately;
  - after every command (with `"r"` set);
  - whenever something the keychain shows changes on the phone (fullness/joy percent, treats, mood).
- Slow decay alone triggers nothing. Wake up, connect, and read (or write `REFRESH`) to catch up.
  The phone runs no timers for the keychain.

Value: compact UTF-8 JSON, at most 244 bytes:

```json
{"v":1,"f":82,"j":75,"t":3,"m":"happy","s":0,"u":1732612345678,"r":"fed","l":"nom nom! ♡"}
```

| Key | Type | Meaning |
| :--- | :--- | :--- |
| `v` | int | Protocol version (`1`) |
| `f` | int 0–100 | Fullness percent |
| `j` | int 0–100 | Joy percent |
| `t` | int 0–9 | Treats in the bag |
| `m` | string | Mood: `happy`, `content`, `hungry`, `sulky`, `sleepy` |
| `s` | 0/1 | `1` while sleepy (`m == "sleepy"`) |
| `u` | int64 | Phone wall-clock ms when the pet state was last updated (0 = never) |
| `r` | string, optional | Reaction to the command just handled: `fed`, `noTreats`, `full` (tummy already full, no treat spent), `pet`. Absent otherwise. |
| `l` | string | A dialogue line from the active mascot's pools (custom mascot lines when installed). May contain kaomoji/emoji; trimmed to fit with a trailing `…`. |

New keys may be added in later versions; ignore unknown keys.

### Command: `1281dbd2-0fd6-4734-99b5-8f45f5403402`
Properties `WRITE | WRITE_NO_RESPONSE`. Value: **exactly one byte**.

| Byte | Command | Phone action | Reaction pushed |
| :--- | :--- | :--- | :--- |
| `0x01` | FEED | `PetManager.tryFeed()` | `fed`, `noTreats` or `full` |
| `0x02` | PET | `PetManager.pet()` (joy has a 5 s cooldown) | `pet` |
| `0x03` | REFRESH | `PetManager.refresh()` | none (mood line) |

Other lengths are rejected with *Invalid Attribute Value Length* (`0x0D`), and unknown bytes with
*Request Not Supported* (`0x06`). After a command, the updated state is notified to every subscriber.

## Suggested firmware loop

1. Deep sleep. On a button press or timer, wake and scan for the service UUID (or reconnect to the bonded address).
2. Connect → request MTU 247 → (bond on first run) → subscribe to State.
3. Render the first notification on the e-paper; send `0x01`/`0x02` from the buttons and render the replies.
4. Disconnect and sleep again. The phone resumes advertising on its own.
