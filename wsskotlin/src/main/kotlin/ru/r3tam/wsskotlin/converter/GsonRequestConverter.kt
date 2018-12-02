package ru.r3tam.wsskotlin.converter

import com.google.gson.Gson
import com.google.gson.TypeAdapter
import ru.r3tam.wsskotlin.WebSocketConverter
import java.nio.charset.Charset

/**
 * Gson-converter for requests
 */
class GsonRequestConverter<T> constructor(
        private val gson: Gson,
        private val adapter: TypeAdapter<T>
) : WebSocketConverter<T, String> {

    override fun convert(value: T?): String {
        return adapter.toJson(value)
    }

    companion object {
        private val UTF_8 = Charset.forName("UTF-8")
    }
}
