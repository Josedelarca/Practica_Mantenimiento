package com.example.pruebaderoom.data

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.File
import java.io.FileInputStream

class ProgressRequestBody(
    private val file: File,
    private val contentType: String,
    private val onProgress: (percent: Int) -> Unit
) : RequestBody() {

    override fun contentType(): MediaType? = MediaType.parse(contentType)

    override fun contentLength(): Long = file.length()

    override fun writeTo(sink: BufferedSink) {
        val fileLength = file.length()
        val buffer = ByteArray(2048)
        val inputStream = FileInputStream(file)
        var uploaded = 0L

        try {
            var read: Int
            while (inputStream.read(buffer).also { read = it } != -1) {
                uploaded += read.toLong()
                sink.write(buffer, 0, read)
                val progress = ((uploaded.toDouble() / fileLength.toDouble()) * 100).toInt()
                onProgress(progress)
            }
        } finally {
            inputStream.close()
        }
    }
}
