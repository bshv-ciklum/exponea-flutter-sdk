package com.exponea

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.NonNull
import com.exponea.data.ConsentEncoder
import com.exponea.data.Customer
import com.exponea.data.Event
import com.exponea.data.ExponeaConfigurationParser
import com.exponea.data.OpenedPush
import com.exponea.data.ReceivedPush
import com.exponea.data.RecommendationEncoder
import com.exponea.data.RecommendationOptionsEncoder
import com.exponea.exception.ExponeaException
import com.exponea.sdk.Exponea
import com.exponea.sdk.models.CustomerIds
import com.exponea.sdk.models.ExponeaConfiguration
import com.exponea.sdk.models.FlushMode
import com.exponea.sdk.models.FlushPeriod
import com.exponea.sdk.models.PropertiesList
import com.exponea.sdk.util.Logger
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.EventChannel.EventSink
import io.flutter.plugin.common.EventChannel.StreamHandler
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import java.util.concurrent.TimeUnit

private const val TAG = "ExponeaPlugin"

/**
 * Exponea Android Plugin
 */
class ExponeaPlugin : FlutterPlugin, ActivityAware {
    companion object {
        private const val CHANNEL_NAME = "com.exponea"
        private const val STREAM_NAME_OPENED_PUSH = "$CHANNEL_NAME/opened_push"
        private const val STREAM_NAME_RECEIVED_PUSH = "$CHANNEL_NAME/received_push"

        fun handleCampaignIntent(intent: Intent?, context: Context) {
            // TODO-EXF-8 : Exponea.handleCampaignIntent(intent, context)
        }
    }

    private var channel: MethodChannel? = null
    private var methodHandler: ExponeaMethodHandler? = null
    private var openedPushChannel: EventChannel? = null
    private var openedPushStreamHandler: OpenedPushStreamHandler? = null
    private var receivedPushChannel: EventChannel? = null

    override fun onAttachedToEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        val context = binding.applicationContext

        channel = MethodChannel(binding.binaryMessenger, CHANNEL_NAME).apply {
            val handler = ExponeaMethodHandler(context)
            setMethodCallHandler(handler)
            methodHandler = handler
        }
        openedPushChannel = EventChannel(binding.binaryMessenger, STREAM_NAME_OPENED_PUSH).apply {
            val handler = OpenedPushStreamHandler()
            setStreamHandler(handler)
            openedPushStreamHandler = handler
        }
        receivedPushChannel = EventChannel(binding.binaryMessenger, STREAM_NAME_RECEIVED_PUSH).apply {
            val handler = ReceivedPushStreamHandler()
            setStreamHandler(handler)
        }
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        channel?.setMethodCallHandler(null)
        channel = null
        methodHandler?.activity = null
        methodHandler = null
        openedPushChannel?.setStreamHandler(null)
        openedPushChannel = null
        receivedPushChannel?.setStreamHandler(null)
        receivedPushChannel = null
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        methodHandler?.activity = binding.activity
    }

    override fun onDetachedFromActivity() {
        methodHandler?.activity = null
    }

    override fun onDetachedFromActivityForConfigChanges() {
        onDetachedFromActivity()
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        onAttachedToActivity(binding)
    }
}

@Suppress("UNCHECKED_CAST")
private class ExponeaMethodHandler(private val context: Context) : MethodCallHandler {
    companion object {
        private const val METHOD_CONFIGURE = "configure"
        private const val METHOD_IS_CONFIGURED = "isConfigured"
        private const val METHOD_GET_CUSTOMER_COOKIE = "getCustomerCookie"
        private const val METHOD_IDENTIFY_CUSTOMER = "identifyCustomer"
        private const val METHOD_ANONYMIZE = "anonymize"
        private const val METHOD_GET_DEFAULT_PROPERTIES = "getDefaultProperties"
        private const val METHOD_SET_DEFAULT_PROPERTIES = "setDefaultProperties"
        private const val METHOD_FLUSH = "flush"
        private const val METHOD_GET_FLUSH_MODE = "getFlushMode"
        private const val METHOD_SET_FLUSH_MODE = "setFlushMode"
        private const val METHOD_GET_FLUSH_PERIOD = "getFlushPeriod"
        private const val METHOD_SET_FLUSH_PERIOD = "setFlushPeriod"
        private const val METHOD_TRACK_EVENT = "trackEvent"
        private const val METHOD_TRACK_SESSION_START = "trackSessionStart"
        private const val METHOD_TRACK_SESSION_END = "trackSessionEnd"
        private const val METHOD_FETCH_CONSENTS = "fetchConsents"
        private const val METHOD_FETCH_RECOMMENDATIONS = "fetchRecommendations"
        private const val METHOD_GET_LOG_LEVEL = "getLogLevel"
        private const val METHOD_SET_LOG_LEVEL = "setLogLevel"
        private const val METHOD_CHECK_PUSH_SETUP = "checkPushSetup"
    }

    var activity: Context? = null
    private var configuration: ExponeaConfiguration? = null
    private val handler: Handler = Handler(Looper.getMainLooper())

    override fun onMethodCall(call: MethodCall, result: Result) {
        Log.i(TAG, "onMethodCall(${call.method})")

        when (call.method) {
            METHOD_CONFIGURE -> {
                configure(call.arguments, result)
            }
            METHOD_IS_CONFIGURED -> {
                isConfigured(result)
            }
            METHOD_GET_CUSTOMER_COOKIE -> {
                getCustomerCookie(result)
            }
            METHOD_IDENTIFY_CUSTOMER -> {
                identifyCustomer(call.arguments, result)
            }
            METHOD_ANONYMIZE -> {
                anonymize(call.arguments, result)
            }
            METHOD_GET_DEFAULT_PROPERTIES -> {
                getDefaultProperties(result)
            }
            METHOD_SET_DEFAULT_PROPERTIES -> {
                setDefaultProperties(call.arguments, result)
            }
            METHOD_FLUSH -> {
                flush(result)
            }
            METHOD_GET_FLUSH_MODE -> {
                getFlushMode(result)
            }
            METHOD_SET_FLUSH_MODE -> {
                setFlushMode(call.arguments, result)
            }
            METHOD_GET_FLUSH_PERIOD -> {
                getFlushPeriod(result)
            }
            METHOD_SET_FLUSH_PERIOD -> {
                setFlushPeriod(call.arguments, result)
            }
            METHOD_TRACK_EVENT -> {
                trackEvent(call.arguments, result)
            }
            METHOD_TRACK_SESSION_START -> {
                trackSessionStart(call.arguments, result)
            }
            METHOD_TRACK_SESSION_END -> {
                trackSessionEnd(call.arguments, result)
            }
            METHOD_FETCH_CONSENTS -> {
                fetchConsents(result)
            }
            METHOD_FETCH_RECOMMENDATIONS -> {
                fetchRecommendations(call.arguments, result)
            }
            METHOD_GET_LOG_LEVEL -> {
                getLogLevel(result)
            }
            METHOD_SET_LOG_LEVEL -> {
                setLogLevel(call.arguments, result)
            }
            METHOD_CHECK_PUSH_SETUP -> {
                checkPushSetup(result)
            }
            else -> {
                result.notImplemented()
            }
        }
    }

    private fun requireConfigured() {
        if (!Exponea.isInitialized) {
            throw ExponeaException.notConfigured()
        }
    }

    private fun requireNotConfigured() {
        if (Exponea.isInitialized) {
            throw ExponeaException.alreadyConfigured()
        }
    }

    private fun <T> runWithResult(result: Result, block: () -> T) {
        try {
            val res = block()
            result.success(res)
        } catch (e: Exception) {
            result.error(TAG, e.message, null)
        }
    }

    private fun runWithNoResult(result: Result, block: () -> Unit) {
        try {
            block()
            result.success(null)
        } catch (e: Exception) {
            result.error(TAG, e.message, null)
        }
    }

    private fun runAsync(result: Result, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            result.error(TAG, e.message, null)
        }
    }

    private fun configure(args: Any?, result: Result) = runWithNoResult(result) {
        requireNotConfigured()
        val data = args as Map<String, Any?>
        val configuration = ExponeaConfigurationParser().parseConfig(data)
        Exponea.init(activity ?: context, configuration)
        this.configuration = configuration
        Exponea.notificationDataCallback = { ReceivedPushStreamHandler.handle(ReceivedPush(it)) }
    }

    private fun isConfigured(result: Result) = runWithResult<Boolean>(result) {
        return@runWithResult Exponea.isInitialized
    }

    private fun getCustomerCookie(result: Result) = runWithResult<String>(result) {
        requireConfigured()
        return@runWithResult Exponea.customerCookie!!
    }

    private fun identifyCustomer(args: Any?, result: Result) = runWithNoResult(result) {
        requireConfigured()
        val data = args as Map<String, Any?>
        val customer = Customer.fromMap(data)
        Exponea.identifyCustomer(
                CustomerIds(HashMap(customer.ids)),
                PropertiesList(HashMap(customer.properties))
        )
    }

    private fun anonymize(args: Any?, result: Result) = runWithNoResult(result) {
        requireConfigured()
        val data = args as Map<String, Any?>
        val configChange = ExponeaConfigurationParser().parseConfigChange(data, configuration!!.baseURL)
        if (configChange.project != null && configChange.mapping != null) {
            Exponea.anonymize(configChange.project, configChange.mapping)
        } else if (configChange.project != null) {
            Exponea.anonymize(configChange.project)
        } else if (configChange.mapping != null) {
            Exponea.anonymize(projectRouteMap = configChange.mapping)
        } else {
            Exponea.anonymize()
        }
    }

    private fun getDefaultProperties(result: Result) = runWithResult<Map<String, Any>>(result) {
        requireConfigured()
        Exponea.defaultProperties
    }

    private fun setDefaultProperties(args: Any?, result: Result) = runWithNoResult(result) {
        requireConfigured()
        val data = args as Map<String, Any>
        Exponea.defaultProperties = HashMap(data)
    }

    private fun flush(result: Result) = runWithNoResult(result) {
        requireConfigured()
        if (Exponea.flushMode != FlushMode.MANUAL) {
            throw ExponeaException.flushModeNotManual()
        }
        Exponea.flushData()
    }

    private fun getFlushMode(result: Result) = runWithResult(result) {
        requireConfigured()
        return@runWithResult Exponea.flushMode.name
    }

    private fun setFlushMode(args: Any?, result: Result) = runWithNoResult(result) {
        requireConfigured()
        val data = args as String
        Exponea.flushMode = FlushMode.valueOf(data)
    }

    private fun getFlushPeriod(result: Result) = runWithResult(result) {
        requireConfigured()
        if (Exponea.flushMode != FlushMode.PERIOD) {
            throw ExponeaException.flushModeNotPeriodic()
        }
        val period = Exponea.flushPeriod
        return@runWithResult period.timeUnit.toSeconds(period.amount)
    }

    private fun setFlushPeriod(args: Any?, result: Result) = runWithNoResult(result) {
        requireConfigured()
        if (Exponea.flushMode != FlushMode.PERIOD) {
            throw ExponeaException.flushModeNotPeriodic()
        }
        val data = args as Int
        Exponea.flushPeriod = FlushPeriod(data.toLong(), TimeUnit.SECONDS)
    }

    private fun trackEvent(args: Any?, result: Result) = runWithNoResult(result) {
        requireConfigured()
        val data = args as Map<String, Any?>
        val event = Event.fromMap(data)
        Exponea.trackEvent(
                PropertiesList(HashMap(event.properties)),
                event.timestamp,
                event.name
        )
    }

    private fun trackSessionStart(args: Any?, result: Result) = runWithNoResult(result) {
        requireConfigured()
        val timestamp = args as? Double
        if (timestamp != null) {
            Exponea.trackSessionStart(timestamp)
        } else {
            Exponea.trackSessionStart()
        }
    }

    private fun trackSessionEnd(args: Any?, result: Result) = runWithNoResult(result) {
        requireConfigured()
        val timestamp = args as? Double
        if (timestamp != null) {
            Exponea.trackSessionEnd(timestamp)
        } else {
            Exponea.trackSessionEnd()
        }
    }

    private fun fetchConsents(result: Result) = runAsync(result) {
        requireConfigured()
        Exponea.getConsents(
                {
                    val data = it.results.map { consent ->
                        ConsentEncoder.encode(consent)
                    }
                    handler.post {
                        result.success(data)
                    }
                },
                {
                    handler.post {
                        result.error(TAG, it.results.message, null)
                    }
                }
        )
    }

    private fun fetchRecommendations(args: Any?, result: Result) = runAsync(result) {
        requireConfigured()
        val inData = args as Map<String, Any?>
        val options = RecommendationOptionsEncoder.decode(inData)
        Log.i(TAG, "aaa $options")
        Exponea.fetchRecommendation(
                options,
                {
                    val outData = it.results.map { recommendation ->
                        RecommendationEncoder.encode(recommendation)
                    }
                    handler.post {
                        result.success(outData)
                    }
                },
                {
                    handler.post {
                        result.error(TAG, it.results.message, null)
                    }
                }
        )
    }

    private fun getLogLevel(result: Result) = runWithResult(result) {
        requireConfigured()
        return@runWithResult Exponea.loggerLevel.name
    }

    private fun setLogLevel(args: Any?, result: Result) = runWithNoResult(result) {
        requireConfigured()
        val logLevel = args as String
        Exponea.loggerLevel = Logger.Level.valueOf(logLevel)
    }

    private fun checkPushSetup(result: Result) = runWithNoResult(result) {
        requireNotConfigured()
        Exponea.checkPushSetup = true
    }
}

/**
 * Handles listeners for opened push notifications.
 */
class OpenedPushStreamHandler : StreamHandler {
    companion object {
        private var currentInstance: OpenedPushStreamHandler? = null

        // We have to hold OpenedPush until plugin is initialized and listener set
        private var pendingData: OpenedPush? = null

        fun handle(push: OpenedPush): Boolean {
            val handled = currentInstance?.internalHandle(push) ?: false
            if (!handled) {
                pendingData = push
            }
            return handled
        }
    }

    init {
        currentInstance = this
    }

    private var eventSink: EventSink? = null

    override fun onListen(arguments: Any?, eSink: EventSink?) {
        eventSink = eSink
        pendingData?.let {
            if (handle(it)) {
                pendingData = null
            }
        }
    }

    override fun onCancel(arguments: Any?) {
        eventSink = null
    }

    private fun internalHandle(push: OpenedPush): Boolean {
        val sink = eventSink
        if (sink != null) {
            sink.success(push.toMap())
            return true
        }
        return false
    }
}

/**
 * Handles listeners for received push notifications.
 */
class ReceivedPushStreamHandler : StreamHandler {
    companion object {
        private var currentInstance: ReceivedPushStreamHandler? = null

        // We have to hold OpenedPush until plugin is initialized and listener set
        private var pendingData: ReceivedPush? = null

        fun handle(push: ReceivedPush): Boolean {
            val handled = currentInstance?.internalHandle(push) ?: false
            if (!handled) {
                pendingData = push
            }
            return handled
        }
    }

    init {
        currentInstance = this
    }

    private var eventSink: EventSink? = null

    override fun onListen(arguments: Any?, eSink: EventSink?) {
        eventSink = eSink
        pendingData?.let {
            if (handle(it)) {
                pendingData = null
            }
        }
    }

    override fun onCancel(arguments: Any?) {
        eventSink = null
    }

    private fun internalHandle(push: ReceivedPush): Boolean {
        val sink = eventSink
        if (sink != null) {
            sink.success(push.toMap())
            return true
        }
        return false
    }
}
