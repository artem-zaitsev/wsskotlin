package ru.r3tam.wsskotlin

import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type

/**
 * Фабрика конвертеров для реквестов и респонсов в сокет
 */
class DefaultWssConverterFactory : WebSocketConverter.Factory() {

    private val responseConverterCreators = HashMap<Class<*>, (TypeToken<*>) -> WebSocketConverter<String, *>>()
    private val initializedResponseConverters = HashMap<Class<*>, WebSocketConverter<String, *>>()


    private val requestConverterCreators = HashMap<Class<*>, (TypeToken<*>) -> WebSocketConverter<*, String>>()
    private val initializedRequestConverters = HashMap<Class<*>, WebSocketConverter<*, String>>()

    override fun responseBodyConverter(type: Type): WebSocketConverter<String, *> {
        return getResponseConverter(TypeToken.get(type) as TypeToken<Any>)!!
    }

    override fun requestBodyConverter(type: Type): WebSocketConverter<*, String> {
        return getRequestConverter(TypeToken.get(type) as TypeToken<String>)!!
    }

    fun putResponseConverter(clazz: Class<*>, converter: (TypeToken<*>) -> WebSocketConverter<String, *>) {
        responseConverterCreators[clazz] = converter
    }

    fun putRequestConverter(clazz: Class<*>, converter: (TypeToken<*>) -> WebSocketConverter<*, String>) {
        requestConverterCreators[clazz] = converter
    }

    private fun <T> getResponseConverter(type: TypeToken<T>): WebSocketConverter<String, T>? {
        val rawType = type.rawType
        var result: WebSocketConverter<String, *>? = initializedResponseConverters[rawType]
        if (result == null) {
            result = tryCreateResponseConverter(type)
            if (result != null) {
                initializedResponseConverters[rawType] = result
            }
        }
        return result as WebSocketConverter<String, T>?
    }

    private fun <T> getRequestConverter(type: TypeToken<T>): WebSocketConverter<T, String>? {
        val rawType = type.rawType
        var result: WebSocketConverter<*, String>? = initializedRequestConverters[rawType]
        if (result == null) {
            result = tryCreateRequestConverter<T>(type)
            if (result != null) {
                initializedRequestConverters[rawType] = result
            }
        }
        return result as WebSocketConverter<T, String>?
    }

    private fun <F, T> tryCreateResponseConverter(type: TypeToken<T>): WebSocketConverter<F, T>? {
        val rawType = type.rawType
        val safeConverterCreator = responseConverterCreators[rawType]
        return safeConverterCreator?.invoke(type) as WebSocketConverter<F, T>?
    }

    private fun <F> tryCreateRequestConverter(type: TypeToken<F>): WebSocketConverter<F, String>? {
        val rawType = type.rawType
        val safeConverterCreator = requestConverterCreators[rawType]
        return safeConverterCreator?.invoke(type) as WebSocketConverter<F, String>?
    }

}
