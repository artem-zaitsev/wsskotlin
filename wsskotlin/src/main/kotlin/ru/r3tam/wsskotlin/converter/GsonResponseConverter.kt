package ru.r3tam.wsskotlin.converter

import com.google.gson.Gson
import com.google.gson.TypeAdapter
import ru.r3tam.wsskotlin.WebSocketConverter
import java.io.IOException
import java.io.StringReader

/**
 * Gson-converter for responses
 */
class GsonResponseConverter<T> constructor(
        private val gson: Gson,
        private val adapter: TypeAdapter<T>
) : WebSocketConverter<String, T> {

    @Throws(IOException::class)
    override fun convert(value: String?): T {
        val jsonReader = gson.newJsonReader(StringReader(value))
        jsonReader.use { jsonReader ->
            return adapter.read(jsonReader)
        }
    }
}

