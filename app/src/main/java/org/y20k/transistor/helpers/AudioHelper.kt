/*
 * AudioHelper.kt
 * Implements the AudioHelper object
 * A AudioHelper provides helper methods for handling audio files
 *
 * This file is part of
 * TRANSISTOR - Radio App for Android
 *
 * Copyright (c) 2015-25 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.transistor.helpers

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.extractor.metadata.icy.IcyHeaders
import androidx.media3.extractor.metadata.icy.IcyInfo
import org.y20k.transistor.Keys
import kotlin.math.min
import java.nio.charset.Charset


/*
 * AudioHelper object
 */
object AudioHelper {

    /* Define log tag */
    private val TAG: String = AudioHelper::class.java.simpleName


    /* Extract duration metadata from audio file */
    fun getDuration(context: Context, audioFileUri: Uri): Long {
        val metadataRetriever: MediaMetadataRetriever = MediaMetadataRetriever()
        var duration: Long = 0L
        try {
            metadataRetriever.setDataSource(context, audioFileUri)
            val durationString = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION) ?: String()
            duration = durationString.toLong()
        } catch (exception: Exception) {
            Log.e(TAG, "Unable to extract duration metadata from audio file")
        }
        return duration
    }


    /* Extract audio stream metadata from Metadata (works only for IceCast metadata) */
    fun getMetadataString(metadata: Metadata): String {
        var metadataString: String = String()
        for (i in 0 until metadata.length()) {
            val entry: Metadata.Entry = metadata.get(i)
            // extract IceCast metadata
            if (entry is IcyInfo) {
                metadataString = smartFixMetadata(entry.title.toString())
            } else if (entry is IcyHeaders) {
                Log.i(TAG, "icyHeaders:" + entry.name + " - " + entry.genre)
            } else {
                Log.w(TAG, "Unsupported metadata received (type = ${entry.javaClass.simpleName})")
            }
            // TODO implement HLS metadata extraction (Id3Frame / PrivFrame)
            // https://exoplayer.dev/doc/reference/com/google/android/exoplayer2/metadata/Metadata.Entry.html
        }
        // ensure a max length of the metadata string
        if (metadataString.isNotEmpty()) {
            metadataString = metadataString.substring(0, min(metadataString.length, Keys.DEFAULT_MAX_LENGTH_OF_METADATA_ENTRY))
        }
        return metadataString
    }


    /* Extract audio stream metadata from MediaMetadata */
    fun getMetadataString(metadata: MediaMetadata): String {
        var metadataString: String = String()
        if (!metadata.title.isNullOrEmpty()) {
            if (!metadataString.contains(metadata.title.toString())) {
                metadataString += metadata.title.toString().trim()
            }
        }
// MediaMetadata often contains station, genre, etc. - but adding those strings to the metadata Transistor displays would produce long and messy strings
//        if (!metadata.artist.isNullOrEmpty()) {
//            if (!metadataString.contains(metadata.artist.toString())) {
//                if (metadataString.isNotEmpty()) {
//                    metadataString += " - "
//                }
//                metadataString += metadata.artist.toString().trim()
//            }
//        }
//        if (!metadata.albumTitle.isNullOrEmpty()) {
//            if (!metadataString.contains(metadata.albumTitle.toString())) {
//                if (metadataString.isNotEmpty()) {
//                    metadataString += " - "
//                }
//                metadataString += metadata.albumTitle.toString().trim()
//            }
//        }
//        if (!metadata.genre.isNullOrEmpty()) {
//            if (!metadataString.contains(metadata.genre.toString())) {
//                if (metadataString.isNotEmpty()) {
//                    metadataString += " - "
//                }
//                metadataString += metadata.genre.toString().trim()
//            }
//        }
//        if (!metadata.station.isNullOrEmpty()) {
//            if (!metadataString.contains(metadata.station.toString())) {
//                if (metadataString.isNotEmpty()) {
//                    metadataString += " - "
//                }
//                metadataString += metadata.station.toString().trim()
//            }
//        }
        // ensure a max length of the metadata string
        if (metadataString.isNotEmpty()) {
            metadataString = metadataString.substring(0, min(metadataString.length, Keys.DEFAULT_MAX_LENGTH_OF_METADATA_ENTRY))
        }
        return smartFixMetadata(metadataString)
    }


    /**
     * 智能修复 ICY 元数据编码
     * 优先尝试 UTF-8，如果解码后无乱码且包含中文则直接使用；
     * 否则依次尝试 UTF-8、GBK、GB18030，选择中文字符最多的结果
     */
    private fun smartFixMetadata(badString: String): String {
        if (badString.isBlank()) return badString

        // 将错误字符串还原为原始字节（ExoPlayer 错误地按 ISO-8859-1 读取）
        val bytes = badString.toByteArray(Charsets.ISO_8859_1)

        // 1. 优先尝试 UTF-8，并检查解码质量
        try {
            val utf8Decoded = String(bytes, Charsets.UTF_8)
            if (!utf8Decoded.contains('�') && utf8Decoded.any { it in '\u4e00'..'\u9fff' }) {
                Log.d(TAG, "UTF-8 decoding successful: $utf8Decoded")
                return utf8Decoded
            }
            Log.d(TAG, "UTF-8 decoding contains replacement char or no Chinese, fallback")
        } catch (e: Exception) {
            Log.w(TAG, "UTF-8 decode failed", e)
        }

        // 2. 回退到多编码比较（UTF-8、GBK、GB18030）
        val encodings = listOf(
            Charsets.UTF_8,
            Charset.forName("GBK"),
            Charset.forName("GB18030")
        )

        var bestResult = badString
        var bestChineseCount = 0

        for (charset in encodings) {
            try {
                val decoded = String(bytes, charset)
                val chineseCount = decoded.count { it in '\u4e00'..'\u9fff' }
                if (chineseCount > bestChineseCount) {
                    bestChineseCount = chineseCount
                    bestResult = decoded
                }
                Log.d(TAG, "Encoding ${charset.name()}: '$decoded' (Chinese count: $chineseCount)")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to decode with ${charset.name()}", e)
            }
        }

        if (bestChineseCount == 0 && bestResult == badString) {
            Log.w(TAG, "No Chinese characters found, returning original: $badString")
        } else {
            Log.d(TAG, "Best result: '$bestResult' (Chinese count: $bestChineseCount)")
        }

        return bestResult
    }

}
