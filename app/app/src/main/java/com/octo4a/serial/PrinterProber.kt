package com.octo4a.serial

import com.hoho.android.usbserial.driver.*


// I should probably come up with a better way of handling those.
// Maybe generation of VID / PID pairs from linux vid / pid db is an option?
data class UsbDeviceId(val vendorId: Int, val productId: Int)

val prolificDevices = listOf(
    UsbDeviceId(1659, 8963),
)

val cdcDevices = listOf(
    // Prusa devices
    UsbDeviceId(11417, 1),
    UsbDeviceId(11417, 2),

    // Bunch of Arduinos
    UsbDeviceId(9025, 16),
    UsbDeviceId(9025, 54),
    UsbDeviceId(9025, 61),
    UsbDeviceId(9025, 62),
    UsbDeviceId(9025, 63),
    UsbDeviceId(9025, 66),
    UsbDeviceId(9025, 67),
    UsbDeviceId(9025, 68),
    UsbDeviceId(9025, 69),
    UsbDeviceId(9025, 73),
    UsbDeviceId(9025, 32822),
    UsbDeviceId(9025, 32824),
    UsbDeviceId(9025, 32825),

    // Teensy (who tf builds printers with those anyways?)
    UsbDeviceId(1155, 5824),

    // Atmel LUFA
    UsbDeviceId(1003, 8260),

    // Maple printer controllers
    UsbDeviceId(7855, 4),
    UsbDeviceId(7855, 41),

    // NXP Armbed
    UsbDeviceId(3368, 516),

    // Marlin CDC driver
    UsbDeviceId(7504, 24617)
)

val ftdiDevices = listOf(
    // Bunch of ftdi devices
    UsbDeviceId(1027, 24577),
    UsbDeviceId(1027, 24592),
    UsbDeviceId(1027, 24593),
    UsbDeviceId(1027, 24596),
    UsbDeviceId(1027, 24597)
)

val ch341Devices = listOf(
    UsbDeviceId(6790, 29987)
)

val cp21xxDevices = listOf(
    UsbDeviceId(4292, 60000),
    UsbDeviceId(4292, 60016),
    UsbDeviceId(4292, 60017),
    UsbDeviceId(4292, 60032),
)

fun getCustomPrinterProber(): ProbeTable {
    val probeTable = ProbeTable()
    prolificDevices.forEach {
        probeTable.addProduct(it.vendorId, it.productId, ProlificSerialDriver::class.java)
    }

    cdcDevices.forEach {
        probeTable.addProduct(it.vendorId, it.productId, CdcAcmSerialDriver::class.java)
    }

    ftdiDevices.forEach {
        probeTable.addProduct(it.vendorId, it.productId, FtdiSerialDriver::class.java)
    }

    ch341Devices.forEach {
        probeTable.addProduct(it.vendorId, it.productId, Ch34xSerialDriver::class.java)
    }

    cp21xxDevices.forEach {
        probeTable.addProduct(it.vendorId, it.productId, Cp21xxSerialDriver::class.java)
    }

    return probeTable
}