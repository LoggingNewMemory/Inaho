package com.kanagawa.yamada.inaho

import android.content.Context
import android.net.Uri
import java.io.InputStream

object FastArtExtractor {

    fun extractArt(context: Context, uri: Uri): ByteArray? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                // Read magic bytes to determine format
                val magic = ByteArray(4)
                stream.mark(32)
                if (stream.read(magic) < 4) return null
                
                if (magic[0] == 'I'.code.toByte() && magic[1] == 'D'.code.toByte() && magic[2] == '3'.code.toByte()) {
                    // It's ID3v2 (MP3)
                    stream.reset()
                    return extractId3v2(stream)
                } else if (magic[0] == 'f'.code.toByte() && magic[1] == 'L'.code.toByte() && magic[2] == 'a'.code.toByte() && magic[3] == 'C'.code.toByte()) {
                    // It's FLAC
                    return extractFlacPicture(stream)
                } else {
                    val typeBytes = ByteArray(4)
                    if (stream.read(typeBytes) == 4 && String(typeBytes) == "ftyp") {
                        // It's MP4/M4A
                        stream.reset()
                        return extractMp4Cover(stream)
                    }
                }
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun extractId3v2(stream: InputStream): ByteArray? {
        val header = ByteArray(10)
        if (stream.read(header) < 10) return null
        
        val version = header[3].toInt()
        val tagSize = synchsafe(header, 6)
        
        var pos = 0
        while (pos < tagSize) {
            val frameHeader = ByteArray(if (version >= 3) 10 else 6)
            val read = stream.read(frameHeader)
            if (read < frameHeader.size) break
            
            val id = if (version >= 3) String(frameHeader, 0, 4) else String(frameHeader, 0, 3)
            val frameSize = if (version == 4) {
                synchsafe(frameHeader, 4)
            } else if (version == 3) {
                ((frameHeader[4].toInt() and 0xFF) shl 24) or
                ((frameHeader[5].toInt() and 0xFF) shl 16) or
                ((frameHeader[6].toInt() and 0xFF) shl 8) or
                (frameHeader[7].toInt() and 0xFF)
            } else {
                ((frameHeader[3].toInt() and 0xFF) shl 16) or
                ((frameHeader[4].toInt() and 0xFF) shl 8) or
                (frameHeader[5].toInt() and 0xFF)
            }
            
            if (frameSize <= 0 || frameSize > tagSize) break
            
            if (id == "APIC" || id == "PIC") {
                val frameData = ByteArray(frameSize)
                if (stream.read(frameData) < frameSize) return null
                
                var offset = 1 // Skip encoding
                // Skip MIME type
                while (offset < frameData.size && frameData[offset].toInt() != 0) offset++
                offset++ // Skip null terminator
                
                offset++ // Skip picture type
                
                // Skip description
                if (frameData[0].toInt() == 1 || frameData[0].toInt() == 2) {
                    // UTF-16, ends with 0x00 0x00
                    while (offset < frameData.size - 1) {
                        if (frameData[offset].toInt() == 0 && frameData[offset+1].toInt() == 0) {
                            offset += 2
                            break
                        }
                        offset++
                    }
                } else {
                    // UTF-8 or ISO-8859-1, ends with 0x00
                    while (offset < frameData.size && frameData[offset].toInt() != 0) offset++
                    offset++
                }
                
                if (offset < frameData.size) {
                    val imgSize = frameData.size - offset
                    val imgData = ByteArray(imgSize)
                    System.arraycopy(frameData, offset, imgData, 0, imgSize)
                    return imgData
                }
            } else {
                // Skip frame
                stream.skip(frameSize.toLong())
            }
            pos += frameHeader.size + frameSize
        }
        return null
    }

    private fun extractFlacPicture(stream: InputStream): ByteArray? {
        var isLast = false
        while (!isLast) {
            val header = ByteArray(4)
            if (stream.read(header) < 4) break
            
            isLast = (header[0].toInt() and 0x80) != 0
            val type = header[0].toInt() and 0x7F
            val length = ((header[1].toInt() and 0xFF) shl 16) or
                         ((header[2].toInt() and 0xFF) shl 8) or
                         (header[3].toInt() and 0xFF)
            
            if (type == 6) { // PICTURE
                val data = ByteArray(length)
                if (stream.read(data) < length) return null
                
                // FLAC picture block:
                // 4 bytes: picture type
                // 4 bytes: MIME type length
                // MIME type string
                // 4 bytes: description length
                // description string
                // 4 bytes: width
                // 4 bytes: height
                // 4 bytes: color depth
                // 4 bytes: colors used
                // 4 bytes: picture data length
                // picture data
                
                var offset = 4
                val mimeLen = readInt32(data, offset); offset += 4 + mimeLen
                val descLen = readInt32(data, offset); offset += 4 + descLen
                offset += 16 // skip w, h, depth, colors
                val picLen = readInt32(data, offset); offset += 4
                
                if (offset + picLen <= data.size) {
                    val imgData = ByteArray(picLen)
                    System.arraycopy(data, offset, imgData, 0, picLen)
                    return imgData
                }
                return null
            } else {
                stream.skip(length.toLong())
            }
        }
        return null
    }

    private fun extractMp4Cover(stream: InputStream): ByteArray? {
        var bytesRead = 0L
        val maxRead = 5 * 1024 * 1024L // Max 5MB scan to find moov
        
        while (bytesRead < maxRead) {
            val header = ByteArray(8)
            val read = stream.read(header)
            if (read < 8) break
            bytesRead += 8
            
            val boxSize = readInt32(header, 0).toLong()
            val boxType = String(header, 4, 4)
            
            if (boxSize < 8) break
            
            if (boxType == "moov" || boxType == "udta" || boxType == "meta" || boxType == "ilst") {
                // Dive into these boxes (meta has a 4 byte version/flags header to skip)
                if (boxType == "meta") {
                    stream.skip(4)
                    bytesRead += 4
                }
                continue
            } else if (boxType == "covr") {
                // Found covr! Now parse the 'data' box inside it
                val dataHeader = ByteArray(8)
                if (stream.read(dataHeader) < 8) break
                val dataSize = readInt32(dataHeader, 0)
                val dataType = String(dataHeader, 4, 4)
                if (dataType == "data" && dataSize > 16) {
                    stream.skip(8) // Skip version/flags + 4 reserved bytes
                    val imgSize = dataSize - 16
                    val imgData = ByteArray(imgSize)
                    if (stream.read(imgData) == imgSize) return imgData
                }
                break
            } else {
                // Skip the rest of this box
                val skipSize = boxSize - 8
                if (skipSize > maxRead - bytesRead) break
                stream.skip(skipSize)
                bytesRead += skipSize
            }
        }
        return null
    }

    private fun synchsafe(bytes: ByteArray, offset: Int): Int {
        return ((bytes[offset].toInt() and 0x7F) shl 21) or
               ((bytes[offset+1].toInt() and 0x7F) shl 14) or
               ((bytes[offset+2].toInt() and 0x7F) shl 7) or
               (bytes[offset+3].toInt() and 0x7F)
    }

    private fun readInt32(bytes: ByteArray, offset: Int): Int {
        if (offset + 4 > bytes.size) return 0
        return ((bytes[offset].toInt() and 0xFF) shl 24) or
               ((bytes[offset+1].toInt() and 0xFF) shl 16) or
               ((bytes[offset+2].toInt() and 0xFF) shl 8) or
               (bytes[offset+3].toInt() and 0xFF)
    }
}
