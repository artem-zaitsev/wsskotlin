package ru.r3tam.wsskotlin

import io.reactivex.Flowable
import io.reactivex.annotations.NonNull
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.processors.PublishProcessor
import okhttp3.*
import okio.ByteString
import java.lang.reflect.Type
import java.util.*


/**
 * Rx-wrapper on WebSocket
 *
 * Apply converters from [WebSocketConverter.Factory] to requests and responses
 */
class RxWebSocket(private val client: OkHttpClient) {

    companion object {
        val INTERNAL_ERROR = 500
    }

    private lateinit var request: Request
    private var originalWebSocket: WebSocket? = null

    private var converterFactories: List<WebSocketConverter.Factory> = ArrayList()
    private var userRequestedClose = false

    private val messagePublishers: MutableMap<Type, PublishProcessor<Message>> = hashMapOf()
    private val publishProcessor = PublishProcessor.create<Event>()

    private val compositeDisposables: CompositeDisposable = CompositeDisposable()
    private val eventStreamsMap: (Type) -> Flowable<Message> = { type -> messagePublishers[type]!! }

    /**
     * Connect to socket
     */
    fun connect() {
        doConnect()
    }

    /**
     * Listen a parsed message from socket.
     * If there are any error return ParsedMessage(null)
     *
     * @param clazz expected data type
     */
    fun <T : Any> listenMessages(clazz: Class<T>): Flowable<ParsedMessage<T>> {
        messagePublishers[clazz] = PublishProcessor.create<Message>()
        compositeDisposables.add(
                publishProcessor
                        .ofType(Message::class.java)
                        .subscribe({
                            messagePublishers[clazz]?.onNext(it)
                        }, {
                            publishProcessor.onNext(Failed(it))
                        })
        )
        return eventStreamsMap(clazz)
                .ofType(Message::class.java)
                .map {
                    try {
                        val data = responseConverter<T>(clazz)?.convert(it.message)!!
                        ParsedMessage(data)
                    } catch (e: Throwable) {
                        ParsedMessage<T>(null)
                    }
                }
    }

    /**
     * Listen to any events from socket
     */
    fun listen(): Flowable<Event> {
        return publishProcessor
    }

    /**
     * Send bytes to socket
     * @param message
     */
    fun send(message: ByteArray) {
        sendMessageToSocket(message)
    }

    /**
     * Send message to socket
     *
     * @param T data type of message
     * @param message
     */
    fun <T : Any> send(message: T) {
        sendMessageToSocket(message)
    }

    /**
     * Disconnect from socket
     * @param code
     * @param reason
     */
    fun disconnect(code: Int, reason: String) {
        doDisconnect(code, reason)
    }

    /**
     * Cancel connection to socket
     *
     * webSocketClient.cancel()
     */
    fun cancel() {
        doCancel()
    }

    private fun <T : Event> sendEventToStream(event: T) {
        publishProcessor.onNext(event)
    }

    private fun doConnect() {
        if (originalWebSocket != null) {
            sendEventToStream(Open())
            return
        }

        client.newWebSocket(request, webSocketListener())
    }

    private fun doDisconnect(code: Int, reason: String) {
        requireNotNull(originalWebSocket, "Expected an open websocket")
        userRequestedClose = true

        originalWebSocket?.close(code, reason)
    }

    private fun doCancel() {
        requireNotNull(originalWebSocket, "Expected an open websocket")
        userRequestedClose = true
        originalWebSocket?.cancel()
    }

    private fun sendMessageToSocket(message: ByteArray) {
        requireNotNull(originalWebSocket, "Expected an open websocket")
        requireNotNull(message, "Expected a non null message")

        originalWebSocket?.send(ByteString.of(*message))
    }

    private fun <T : Any> sendMessageToSocket(message: T) {
        requireNotNull(originalWebSocket, "Expected an open websocket")
        requireNotNull(message, "Expected a non null message")

        val converter = requestConverter<T>(message.javaClass)
        val sendString = { string: String? ->
            originalWebSocket?.send(string)
        }

        sendString(converter?.convert(message) ?: message as? String)
    }

    private fun setSocket(originalWebSocket: WebSocket?) {
        this.originalWebSocket = originalWebSocket
        userRequestedClose = false
    }

    private fun <T> responseConverter(type: Type): WebSocketConverter<String, T>? {
        return converterFactories
                .mapNotNull { it.responseBodyConverter(type) }
                .first() as? WebSocketConverter<String, T>
    }

    private fun <T> requestConverter(type: Type): WebSocketConverter<T, String>? {
        return converterFactories
                .mapNotNull { it.requestBodyConverter(type) }
                .first() as? WebSocketConverter<T, String>
    }

    private fun <T> requireNotNull(`object`: T?, message: String): T {
        if (`object` == null) {
            throw IllegalStateException(message)
        }
        return `object`
    }

    private fun webSocketListener(): WebSocketListener {
        return WssListener(
                onOpenAction = { webSocket: WebSocket, response: Response ->
                    setSocket(webSocket)
                    sendEventToStream(Open())
                },
                onMessageAction = { wss, text, bytes -> sendEventToStream(Message(text, bytes)) },
                onFailureAction = { wss, t, response ->
                    sendEventToStream(Failed(t))

                    compositeDisposables.dispose()
                    setSocket(null)
                },
                onClosedAction = { wss: WebSocket, code: Int, reason: String ->
                    if (userRequestedClose) {
                        if (publishProcessor.hasSubscribers()) {
                            publishProcessor.onNext(Closed(code, reason))
                            publishProcessor.onComplete()
                        }
                    } else {
                        sendEventToStream(Closed(code, reason))
                    }

                    compositeDisposables.dispose()
                    setSocket(null)
                })
    }

    /**
     * Builder class for creating rx websockets.
     */
    class Builder {
        private lateinit var request: Request

        private val converterFactories = ArrayList<WebSocketConverter.Factory>()
        private lateinit var client: OkHttpClient

        fun setClient(client: OkHttpClient) = apply {
            this.client = client
        }

        @NonNull
        fun request(request: Request): Builder = apply {
            this.request = request
        }

        @NonNull
        fun addConverterFactory(factory: WebSocketConverter.Factory?): Builder = apply {
            factory?.let { converterFactories.add(it) }
        }

        @NonNull
        @Throws(IllegalStateException::class)
        fun build(): RxWebSocket {
            val rxWebSocket = RxWebSocket(client)
            rxWebSocket.request = request
            rxWebSocket.converterFactories = converterFactories
            return rxWebSocket
        }

        @NonNull
        fun build(@NonNull wssUrl: String?): RxWebSocket {
            if (wssUrl == null || wssUrl.isEmpty()) {
                throw IllegalStateException("Websocket address cannot be null or empty")
            }

            request = Request.Builder().url(wssUrl).get().build()

            val rxWebSocket = RxWebSocket(client)
            rxWebSocket.converterFactories = converterFactories
            rxWebSocket.request = request
            return rxWebSocket
        }
    }

    //region Events
    interface Event {
        val status: WssStatus
    }

    class Open : Event {
        override val status: WssStatus
            get() = WssStatus.OPENED
    }

    class Message(
            val message: String? = null,
            val messageBytes: ByteString? = null
    ) : Event {
        override val status: WssStatus
            get() = WssStatus.OPENED
    }

    class ParsedMessage<T>(
            val data: T?,
            val error: Throwable? = null
    ) : Event {
        override val status: WssStatus
            get() = WssStatus.OPENED
    }

    class Failed(val t: Throwable) : Event {
        override val status: WssStatus
            get() = WssStatus.FAILED
    }

    class Closed(val code: Int, val reason: String) : Event {
        override val status: WssStatus
            get() = WssStatus.CLOSED
    }

//endregion Events
}

