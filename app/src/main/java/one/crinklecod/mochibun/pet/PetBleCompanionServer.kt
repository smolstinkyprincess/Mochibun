package one.crinklecod.mochibun.pet

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import one.crinklecod.mochibun.data.prefs.PreferencesManager
import one.crinklecod.mochibun.data.repository.CustomMascotManager

/**
 * Opt-in BLE GATT server for the MicroMochi e-paper keychain (protocol: [PetBleProtocol]).
 *
 * Offline by construction: plain `android.bluetooth` GATT + advertising, no INTERNET, no location
 * (a GATT server and advertiser never scan, so no location permission is involved on any API).
 *
 * Battery: advertises in LOW_POWER mode (~1 s interval) at low TX power only while no keychain is
 * subscribed, and runs no timers. State is pushed only when [PetManager.state] changes something
 * the keychain shows, or right after one of its commands; decay over time is picked up when the
 * keychain reads or sends REFRESH.
 *
 * Threading: GATT/advertising callbacks arrive on binder threads and hop onto [scope], a serial
 * worker, so every mutable field below is only touched there.
 *
 * Lint's MissingPermission can't follow [hasBlePermissions]; every radio call happens after it
 * passed in [sync], and SecurityException (permission revoked while running) stops the server.
 */
@SuppressLint("MissingPermission")
object PetBleCompanionServer {
    private const val TAG = "PetBle"

    enum class Status { OFF, UNSUPPORTED, NEEDS_PERMISSION, BLUETOOTH_OFF, ADVERTISING, CONNECTED }

    private val _status = MutableStateFlow(Status.OFF)
    /** For the Settings row subtitle. */
    val status: StateFlow<Status> = _status.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    @Volatile
    private var appContext: Context? = null

    private var gattServer: BluetoothGattServer? = null
    private var stateCharacteristic: BluetoothGattCharacteristic? = null
    private var advertising = false
    private var stateJob: Job? = null
    private var receiverRegistered = false

    private val subscribers = LinkedHashSet<BluetoothDevice>()
    private val mtuByDevice = HashMap<BluetoothDevice, Int>()
    /** One payload per device for long reads (offset > 0 continues the same snapshot). */
    private val readCache = HashMap<BluetoothDevice, ByteArray>()
    /** Android allows one notification in flight per server; newer payloads replace queued ones. */
    private val pendingNotify = LinkedHashMap<BluetoothDevice, ByteArray>()
    private var inFlightDevice: BluetoothDevice? = null
    private var lastNotifiedKey: PetBleSnapshot? = null
    private var lastLine: String? = null
    private var warnedSmallMtu = false

    private val hasStepSensor: Boolean by lazy {
        val sm = appContext?.getSystemService(SensorManager::class.java)
        sm?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null
    }

    /** Call once from the Application. Follows the Settings switch from then on. */
    fun init(context: Context) {
        if (appContext != null) return
        synchronized(this) {
            if (appContext != null) return
            appContext = context.applicationContext
        }
        scope.launch {
            PreferencesManager.bleCompanionEnabled.collect { sync() }
        }
    }

    /** Re-checks permissions / adapter state, e.g. after the Settings permission prompt returns. */
    fun requestSync() {
        scope.launch { sync() }
    }

    /** Runtime permissions still missing for the companion (empty below Android 12: install-time). */
    fun missingRuntimePermissions(context: Context): Array<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return emptyArray()
        return arrayOf(Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT)
            .filter { context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
            .toTypedArray()
    }

    fun hasBlePermissions(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            missingRuntimePermissions(context).isEmpty()
        } else {
            context.checkSelfPermission(Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
                context.checkSelfPermission(Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED
        }

    // ---- lifecycle (on scope) ----

    private fun sync() {
        val ctx = appContext ?: return
        if (!PreferencesManager.bleCompanionEnabled.value) {
            setAdapterReceiver(ctx, register = false)
            stop("disabled in settings", Status.OFF)
            return
        }
        setAdapterReceiver(ctx, register = true)

        if (!ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            stop("no BLE hardware", Status.UNSUPPORTED)
            return
        }
        if (!hasBlePermissions(ctx)) {
            stop("Bluetooth permission not granted", Status.NEEDS_PERMISSION)
            return
        }
        val manager = ctx.getSystemService(BluetoothManager::class.java)
        val adapter = manager?.adapter
        if (manager == null || adapter == null) {
            stop("no Bluetooth adapter", Status.UNSUPPORTED)
            return
        }
        try {
            if (!adapter.isEnabled) {
                stop("Bluetooth is off", Status.BLUETOOTH_OFF)
                return
            }
            if (!adapter.isMultipleAdvertisementSupported || adapter.bluetoothLeAdvertiser == null) {
                stop("BLE advertising unsupported", Status.UNSUPPORTED)
                return
            }
            if (gattServer == null) start(ctx, manager)
        } catch (e: SecurityException) {
            Log.w(TAG, "[PetBle] permission revoked while starting", e)
            stop("permission revoked", Status.NEEDS_PERMISSION)
        }
    }

    private fun start(ctx: Context, manager: BluetoothManager) {
        val server = manager.openGattServer(ctx, gattCallback)
        if (server == null) {
            Log.w(TAG, "[PetBle] openGattServer returned null")
            _status.value = Status.UNSUPPORTED
            return
        }
        // Encrypted permissions make Android answer "insufficient encryption" until the keychain
        // bonds, so a stranger in range can't read her or spend treats.
        val state = BluetoothGattCharacteristic(
            PetBleProtocol.STATE_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED,
        )
        state.addDescriptor(
            BluetoothGattDescriptor(
                PetBleProtocol.CCCD_UUID,
                BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED or BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED,
            )
        )
        val command = BluetoothGattCharacteristic(
            PetBleProtocol.COMMAND_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED,
        )
        val service = BluetoothGattService(PetBleProtocol.SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        service.addCharacteristic(state)
        service.addCharacteristic(command)

        gattServer = server
        stateCharacteristic = state
        // Advertising starts from onServiceAdded, once the service is actually registered.
        if (!server.addService(service)) {
            Log.w(TAG, "[PetBle] addService failed")
            stop("addService failed", Status.UNSUPPORTED)
            return
        }
        stateJob = scope.launch { PetManager.state.collect { onPetStateChanged() } }
        Log.i(TAG, "[PetBle] GATT server opened")
    }

    private fun stop(reason: String, newStatus: Status) {
        val wasRunning = gattServer != null
        stateJob?.cancel()
        stateJob = null
        stopAdvertising()
        try {
            gattServer?.close()
        } catch (e: SecurityException) {
            Log.w(TAG, "[PetBle] close failed", e)
        }
        gattServer = null
        stateCharacteristic = null
        subscribers.clear()
        mtuByDevice.clear()
        readCache.clear()
        pendingNotify.clear()
        inFlightDevice = null
        lastNotifiedKey = null
        _status.value = newStatus
        if (wasRunning) Log.i(TAG, "[PetBle] GATT server closed ($reason)") else Log.i(TAG, "[PetBle] idle ($reason)")
    }

    private fun setAdapterReceiver(ctx: Context, register: Boolean) {
        if (register == receiverRegistered) return
        if (register) {
            ctx.registerReceiver(adapterReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        } else {
            try {
                ctx.unregisterReceiver(adapterReceiver)
            } catch (_: IllegalArgumentException) {
            }
        }
        receiverRegistered = register
    }

    private val adapterReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val s = intent?.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR) ?: return
            if (s == BluetoothAdapter.STATE_ON || s == BluetoothAdapter.STATE_TURNING_OFF ||
                s == BluetoothAdapter.STATE_OFF
            ) {
                requestSync()
            }
        }
    }

    // ---- advertising (on scope) ----

    private fun startAdvertising() {
        if (advertising || gattServer == null) return
        val ctx = appContext ?: return
        val advertiser = ctx.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeAdvertiser ?: return
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_LOW)
            .setConnectable(true)
            .setTimeout(0)
            .build()
        val uuid = ParcelUuid(PetBleProtocol.SERVICE_UUID)
        // flags (3) + 128-bit UUID list (18) = 21 of 31 bytes; the tag goes in the scan response.
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceUuid(uuid)
            .build()
        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceData(uuid, PetBleProtocol.ADVERTISED_TAG.toByteArray(Charsets.US_ASCII))
            .build()
        try {
            advertiser.startAdvertising(settings, data, scanResponse, advertiseCallback)
            advertising = true
        } catch (e: SecurityException) {
            Log.w(TAG, "[PetBle] startAdvertising denied", e)
            stop("permission revoked", Status.NEEDS_PERMISSION)
        }
    }

    private fun stopAdvertising() {
        if (!advertising) return
        advertising = false
        try {
            appContext?.getSystemService(BluetoothManager::class.java)
                ?.adapter?.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
            Log.i(TAG, "[PetBle] advertising stopped")
        } catch (e: SecurityException) {
            Log.w(TAG, "[PetBle] stopAdvertising denied", e)
        } catch (e: IllegalStateException) {
            // Adapter already off: nothing left to stop.
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.i(TAG, "[PetBle] advertising as MicroMochi service")
            scope.launch { if (gattServer != null && subscribers.isEmpty()) _status.value = Status.ADVERTISING }
        }

        override fun onStartFailure(errorCode: Int) {
            if (errorCode == ADVERTISE_FAILED_ALREADY_STARTED) return
            Log.w(TAG, "[PetBle] advertising failed: $errorCode")
            scope.launch { advertising = false }
        }
    }

    // ---- GATT callbacks (binder threads -> scope) ----

    private val gattCallback = object : BluetoothGattServerCallback() {
        override fun onServiceAdded(status: Int, service: BluetoothGattService?) {
            scope.launch {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    startAdvertising()
                } else {
                    Log.w(TAG, "[PetBle] service registration failed: $status")
                    stop("service registration failed", Status.UNSUPPORTED)
                }
            }
        }

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            // Any LE link to the phone (watches, earbuds) shows up here, so only a CCCD subscription
            // marks a device as "our keychain"; a disconnect just drops whatever it held.
            if (newState != BluetoothProfile.STATE_DISCONNECTED) return
            scope.launch {
                val wasSubscriber = subscribers.remove(device)
                mtuByDevice.remove(device)
                readCache.remove(device)
                pendingNotify.remove(device)
                if (inFlightDevice == device) {
                    inFlightDevice = null
                    drainNotifications()
                }
                if (wasSubscriber) {
                    Log.i(TAG, "[PetBle] keychain disconnected")
                    onSubscribersChanged()
                }
            }
        }

        override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
            scope.launch { mtuByDevice[device] = mtu }
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic,
        ) {
            scope.launch {
                val server = gattServer ?: return@launch
                if (characteristic.uuid != PetBleProtocol.STATE_CHAR_UUID) {
                    respond(server, device, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, offset, null)
                    return@launch
                }
                val payload = if (offset == 0) {
                    PetManager.refresh()
                    buildPayload(reaction = null, maxBytes = PetBleProtocol.MAX_PAYLOAD_BYTES)
                        .also { readCache[device] = it }
                } else {
                    readCache[device] ?: buildPayload(reaction = null, maxBytes = PetBleProtocol.MAX_PAYLOAD_BYTES)
                }
                if (offset > payload.size) {
                    respond(server, device, requestId, BluetoothGatt.GATT_INVALID_OFFSET, offset, null)
                } else {
                    respond(server, device, requestId, BluetoothGatt.GATT_SUCCESS, offset,
                        payload.copyOfRange(offset, payload.size))
                }
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?,
        ) {
            scope.launch {
                val server = gattServer ?: return@launch
                val command = if (characteristic.uuid == PetBleProtocol.COMMAND_CHAR_UUID &&
                    !preparedWrite && offset == 0
                ) PetBleCommand.fromBytes(value) else null
                if (command == null) {
                    val status = if (value?.size != 1) {
                        BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH
                    } else {
                        BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED
                    }
                    Log.w(TAG, "[PetBle] rejected command write (${value?.size ?: 0} bytes)")
                    if (responseNeeded) respond(server, device, requestId, status, offset, null)
                    return@launch
                }
                if (responseNeeded) respond(server, device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                val reaction = dispatchPetBleCommand(command, PetBleActions.Live)
                Log.i(TAG, "[PetBle] command $command -> ${reaction?.wire ?: "state"}")
                pushState(reaction)
            }
        }

        override fun onDescriptorReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            descriptor: BluetoothGattDescriptor,
        ) {
            scope.launch {
                val server = gattServer ?: return@launch
                val value = if (device in subscribers) {
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                } else {
                    BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                }
                respond(server, device, requestId, BluetoothGatt.GATT_SUCCESS, 0, value)
            }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?,
        ) {
            scope.launch {
                val server = gattServer ?: return@launch
                if (descriptor.uuid != PetBleProtocol.CCCD_UUID || value == null || value.size != 2) {
                    if (responseNeeded) {
                        respond(server, device, requestId, BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, offset, null)
                    }
                    return@launch
                }
                if (responseNeeded) respond(server, device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                val subscribe = value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                val changed = if (subscribe) subscribers.add(device) else subscribers.remove(device)
                if (!changed) return@launch
                Log.i(TAG, if (subscribe) "[PetBle] keychain subscribed" else "[PetBle] keychain unsubscribed")
                onSubscribersChanged()
                if (subscribe) pushStateTo(device, buildLine(null))
            }
        }

        override fun onNotificationSent(device: BluetoothDevice, status: Int) {
            scope.launch {
                if (status != BluetoothGatt.GATT_SUCCESS) Log.w(TAG, "[PetBle] notification failed: $status")
                if (inFlightDevice == device) inFlightDevice = null
                drainNotifications()
            }
        }
    }

    private fun respond(
        server: BluetoothGattServer,
        device: BluetoothDevice,
        requestId: Int,
        status: Int,
        offset: Int,
        value: ByteArray?,
    ) {
        try {
            server.sendResponse(device, requestId, status, offset, value)
        } catch (e: SecurityException) {
            Log.w(TAG, "[PetBle] sendResponse denied", e)
        }
    }

    // ---- state pushes (on scope) ----

    /** Pause advertising while a keychain listens; resume when none is left. */
    private fun onSubscribersChanged() {
        if (gattServer == null) return
        if (subscribers.isEmpty()) {
            _status.value = Status.ADVERTISING
            startAdvertising()
        } else {
            _status.value = Status.CONNECTED
            stopAdvertising()
        }
    }

    /**
     * Snapshot decayed to now *without* storing it: calling [PetManager.refresh] here would emit a
     * new state and re-enter this collector forever.
     */
    private fun snapshotNow(): PetBleSnapshot {
        val clock = PetManager.clock
        val now = clock.nowMs()
        val advanced = PetLogic.advance(PetManager.state.value, now, clock.epochDay(now))
        return PetBleSnapshot.of(advanced, PetLogic.mood(advanced, clock.localHour(now)))
    }

    private fun onPetStateChanged() {
        if (subscribers.isEmpty()) return
        if (snapshotNow().visibleKey() == lastNotifiedKey) return
        pushState(reaction = null)
    }

    private fun buildLine(reaction: PetBleReaction?, mood: PetMood = snapshotNow().mood): String =
        PetBleDialogue.line(reaction, mood, lastLine, hasStepSensor, CustomMascotManager.activeDialogue())
            .also { lastLine = it }

    private fun buildPayload(reaction: PetBleReaction?, maxBytes: Int): ByteArray {
        val snapshot = snapshotNow()
        return PetBleProtocol.encodeState(snapshot, reaction, buildLine(reaction, snapshot.mood), maxBytes)
    }

    /** Sends the current state (+ one shared line) to every subscriber. */
    private fun pushState(reaction: PetBleReaction?) {
        val snapshot = snapshotNow()
        // Set before any await: a command's own state change must not trigger a second push.
        lastNotifiedKey = snapshot.visibleKey()
        if (subscribers.isEmpty()) return
        val line = buildLine(reaction, snapshot.mood)
        for (device in subscribers) enqueue(device, snapshot, reaction, line)
        drainNotifications()
    }

    private fun pushStateTo(device: BluetoothDevice, line: String) {
        val snapshot = snapshotNow()
        lastNotifiedKey = snapshot.visibleKey()
        enqueue(device, snapshot, null, line)
        drainNotifications()
    }

    private fun enqueue(device: BluetoothDevice, snapshot: PetBleSnapshot, reaction: PetBleReaction?, line: String) {
        val budget = PetBleProtocol.payloadBudgetForMtu(mtuByDevice[device] ?: PetBleProtocol.DEFAULT_ATT_MTU)
        val payload = PetBleProtocol.encodeState(snapshot, reaction, line, budget)
        if (payload.size > budget) {
            // Truncated JSON would be worse than nothing; the keychain can still read (long read).
            if (!warnedSmallMtu) {
                Log.w(TAG, "[PetBle] MTU too small for notifications; firmware should request ${PetBleProtocol.TARGET_ATT_MTU}")
                warnedSmallMtu = true
            }
            return
        }
        pendingNotify[device] = payload
    }

    @Suppress("DEPRECATION")
    private fun drainNotifications() {
        val server = gattServer ?: return
        val characteristic = stateCharacteristic ?: return
        while (inFlightDevice == null) {
            val device = pendingNotify.keys.firstOrNull() ?: return
            val payload = pendingNotify.remove(device) ?: continue
            val sent = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    server.notifyCharacteristicChanged(device, characteristic, false, payload) ==
                        BluetoothStatusCodes.SUCCESS
                } else {
                    characteristic.value = payload
                    server.notifyCharacteristicChanged(device, characteristic, false)
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "[PetBle] notify denied", e)
                false
            }
            if (sent) inFlightDevice = device
        }
    }
}
