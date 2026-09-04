package pl.pulsebreath.wear.sensor

import android.content.Context
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicLong
import com.samsung.android.service.health.tracking.ConnectionListener
import com.samsung.android.service.health.tracking.HealthTracker
import com.samsung.android.service.health.tracking.HealthTrackerException
import com.samsung.android.service.health.tracking.HealthTrackingService
import com.samsung.android.service.health.tracking.data.DataPoint
import com.samsung.android.service.health.tracking.data.HealthTrackerType
import com.samsung.android.service.health.tracking.data.ValueKey

internal class SamsungSensorDataSource(
    context: Context,
) : StreamingSensorDataSource {
    private val applicationContext = context.applicationContext

    @Volatile
    private var active = false
    private var service: HealthTrackingService? = null
    private var tracker: HealthTracker? = null
    private var onStatus: ((SensorStreamStatus) -> Unit)? = null
    private var onSample: ((SensorSample) -> Unit)? = null
    private val callbackSequence = AtomicLong(0L)

    private val trackerListener = object : HealthTracker.TrackerEventListener {
        override fun onDataReceived(dataPoints: List<DataPoint>) {
            if (!active) return

            val receivedElapsedMillis = SystemClock.elapsedRealtime()
            val sequence = callbackSequence.getAndIncrement()
            dataPoints.forEachIndexed { index, dataPoint ->
                val heartRate = dataPoint.getValue(ValueKey.HeartRateSet.HEART_RATE)
                val heartRateStatus =
                    dataPoint.getValue(ValueKey.HeartRateSet.HEART_RATE_STATUS)
                val ibiValues =
                    dataPoint.getValue(ValueKey.HeartRateSet.IBI_LIST).orEmpty()
                val ibiStatuses =
                    dataPoint.getValue(ValueKey.HeartRateSet.IBI_STATUS_LIST).orEmpty()

                onSample?.invoke(
                    SamsungHeartRateReadingMapper.map(
                        monotonicTimestampMillis = receivedElapsedMillis,
                        heartRate = heartRate,
                        heartRateStatus = heartRateStatus,
                        ibiValuesMillis = ibiValues,
                        ibiStatuses = ibiStatuses,
                        timing = SensorTiming(
                            receivedElapsedMillis = receivedElapsedMillis,
                            sdkTimestampMillis = dataPoint.timestamp,
                            callbackSequence = sequence,
                            pointIndex = index,
                            pointCount = dataPoints.size,
                            rawIbiCount = ibiValues.size,
                            rawStatusCount = ibiStatuses.size,
                        ),
                    ),
                )
            }
        }

        override fun onFlushCompleted() = Unit

        override fun onError(error: HealthTracker.TrackerError) {
            if (!active) return
            emitStatus(
                SensorStreamState.ERROR,
                when (error) {
                    HealthTracker.TrackerError.PERMISSION_ERROR ->
                        "Heart-rate permission was denied."
                    HealthTracker.TrackerError.SDK_POLICY_ERROR ->
                        "Samsung SDK policy denied access. Check developer mode."
                },
            )
            stopTrackingResources()
        }
    }

    private val connectionListener = object : ConnectionListener {
        override fun onConnectionSuccess() {
            if (!active) return
            val connectedService = service ?: return
            val supported = connectedService.trackingCapability.supportHealthTrackerTypes
                .contains(HealthTrackerType.HEART_RATE_CONTINUOUS)
            if (!supported) {
                emitStatus(
                    SensorStreamState.UNSUPPORTED,
                    "Continuous heart rate with IBI is not supported on this watch.",
                )
                stopTrackingResources()
                return
            }

            runCatching {
                connectedService.getHealthTracker(HealthTrackerType.HEART_RATE_CONTINUOUS)
                    .also { healthTracker ->
                        tracker = healthTracker
                        healthTracker.setEventListener(trackerListener)
                    }
            }.onSuccess {
                emitStatus(SensorStreamState.TRACKING, "Reading Samsung BPM and IBI.")
            }.onFailure {
                emitStatus(SensorStreamState.ERROR, "Could not start the heart-rate tracker.")
                stopTrackingResources()
            }
        }

        override fun onConnectionEnded() {
            if (active) {
                emitStatus(SensorStreamState.ERROR, "Health Sensor Service disconnected.")
                stopTrackingResources()
            }
        }

        override fun onConnectionFailed(error: HealthTrackerException) {
            if (!active) return
            emitStatus(
                SensorStreamState.ERROR,
                when (error.errorCode) {
                    HealthTrackerException.PACKAGE_NOT_INSTALLED ->
                        "Health Sensor Service is not installed."
                    HealthTrackerException.OLD_PLATFORM_VERSION ->
                        "Health Sensor Service must be updated."
                    else -> "Could not connect to Health Sensor Service."
                },
            )
            stopTrackingResources()
        }
    }

    override fun start(
        onStatus: (SensorStreamStatus) -> Unit,
        onSample: (SensorSample) -> Unit,
    ) {
        stop()
        this.onStatus = onStatus
        this.onSample = onSample
        active = true
        emitStatus(SensorStreamState.CONNECTING, "Connecting to Health Sensor Service.")
        service = HealthTrackingService(connectionListener, applicationContext).also {
            it.connectService()
        }
    }

    override fun stop() {
        active = false
        stopTrackingResources()
        onStatus = null
        onSample = null
    }

    private fun stopTrackingResources() {
        active = false
        runCatching { tracker?.unsetEventListener() }
        tracker = null
        runCatching { service?.disconnectService() }
        service = null
    }

    private fun emitStatus(state: SensorStreamState, message: String) {
        onStatus?.invoke(SensorStreamStatus(state, message))
    }
}
