package ru.r3tam.wsskotlin

import java.lang.reflect.Type

interface WebSocketConverter<F, T> {
    @Throws(Throwable::class)
    fun convert(value: F?): T

    /** Creates convertor instances based on a type and target usage.  */
    abstract class Factory {

        abstract fun responseBodyConverter(type: Type): WebSocketConverter<String, *>?

        abstract fun requestBodyConverter(type: Type): WebSocketConverter<*, String>?
    }
}
