package com.octo4a.utils

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.net.wifi.WifiManager
import android.os.Build
import android.text.format.Formatter
import android.util.TypedValue
import android.widget.Toast
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import java.io.ByteArrayOutputStream


fun Int.isBitSet(bit: Int): Boolean {
    return this and (1 shl bit) != 0
}

@ColorInt
fun Context.getColorFromAttr(
    @AttrRes attrColor: Int,
    typedValue: TypedValue = TypedValue(),
    resolveRefs: Boolean = true
): Int {
    theme.resolveAttribute(attrColor, typedValue, resolveRefs)
    return typedValue.data
}

fun Activity.isServiceRunning(service: Class<*>): Boolean {
    val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    manager.getRunningServices(Integer.MAX_VALUE).forEach {
        if (service.name.equals(it.service.className)) {
            return true
        }
    }

    return false
}


fun ByteArray.NV21toJPEG( width: Int, height: Int, quality: Int): ByteArray? {
    val out = ByteArrayOutputStream()
    val yuv = YuvImage(this, ImageFormat.NV21, width, height, null)
    yuv.compressToJpeg(Rect(0, 0, width, height), quality, out)
    return out.toByteArray()
}
//
//fun ImageProxy.YUV420toNV21(): ByteArray {
//    val width: Int = cropRect.width()
//    val height: Int = cropRect.height()
//    val data = ByteArray(width * height * ImageFormat.getBitsPerPixel(format) / 8)
//    val rowData = ByteArray(planes[0].rowStride)
//    var channelOffset = 0
//    var outputStride = 1
//    for (i in planes.indices) {
//        when (i) {
//            0 -> {
//                channelOffset = 0
//                outputStride = 1
//            }
//            1 -> {
//                channelOffset = width * height + 1
//                outputStride = 2
//            }
//            2 -> {
//                channelOffset = width * height
//                outputStride = 2
//            }
//        }
//        val buffer: ByteBuffer = planes[i].buffer
//        val rowStride: Int = planes[i].rowStride
//        val pixelStride: Int = planes[i].pixelStride
//        val shift = if (i == 0) 0 else 1
//        val w = width shr shift
//        val h = height shr shift
//        buffer.position(rowStride * (cropRect.top shr shift) + pixelStride * (cropRect.left shr shift))
//        for (row in 0 until h) {
//            var length: Int
//            if (pixelStride == 1 && outputStride == 1) {
//                length = w
//                buffer.get(data, channelOffset, length)
//                channelOffset += length
//            } else {
//                length = (w - 1) * pixelStride + 1
//                buffer.get(rowData, 0, length)
//                for (col in 0 until w) {
//                    data[channelOffset] = rowData[col * pixelStride]
//                    channelOffset += outputStride
//                }
//            }
//            if (row < h - 1) {
//                buffer.position(buffer.position() + rowStride - length)
//            }
//        }
//    }
//    return data
//}

private fun is64Bit(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        (Build.SUPPORTED_64_BIT_ABIS != null && Build.SUPPORTED_64_BIT_ABIS.isNotEmpty())
    } else {
        !(Build.CPU_ABI != "x86" && Build.CPU_ABI2 != "x86")
    }
}

fun getArchString(): String {
    var arch = System.getProperty("os.arch")!!.toString()

    if (arch != "x86_64" && arch != "i686") {
        arch = if (is64Bit()) {
            "aarch64"
        } else {
            "arm"
        }
    }
    return arch
}

val Context.ipAddress: String
    get() {
        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        return Formatter.formatIpAddress(wm.connectionInfo.ipAddress)
    }