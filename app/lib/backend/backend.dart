import 'dart:async';

import 'package:flutter/services.dart';
import 'package:permission_handler/permission_handler.dart';

const statusSink = const EventChannel('io.feelfreelinux.octo4a/status');
const methodChannel = const MethodChannel('io.feelfreelinux.octo4a/methods');

/// InstallationStatus indicates current state of octoprint installation
enum InstallationStatus {
  INSTALLING_BOOTSTRAP,
  DOWNLOADING_OCTOPRINT,
  INSTALLING_OCTOPRINT,
  BOOTING_OCTOPRINT,
  INSTALLATION_COMPLETE,
}

enum OctoPrintStatus { INSTALLING, STARTING_UP, RUNNING, STOPPED }

var statusStream = StreamController<InstallationStatus>();

Future<bool> validateInstallationStatus() async {
  return methodChannel.invokeMethod("verifyInstallation");
}

/// Methods below are used in particular installation
Future<bool> beginInstallation() async {
  methodChannel.invokeMethod("beginInstallation");
}

Future<void> stopServer() {
  methodChannel.invokeMethod("stopServer");
}

Future<void> startServer() {
  methodChannel.invokeMethod("startServer");
}

Future<void> startCameraServer() async {
  if (await Permission.camera.request().isGranted) {
    methodChannel.invokeMethod("startCameraServer");
  }
}

Future<void> stopCameraServer() async {
  methodChannel.invokeMethod("stopCameraServer");
}

Future<void> queryServerStatus() {
  methodChannel.invokeMethod("queryServerStatus");
}

Future<void> fetchUsbDevices() {
  methodChannel.invokeMethod("queryUsbDevices");
}

Future<void> selectSerialDevice(int baud) {
  methodChannel.invokeMethod("selectUsbDevice", Map.from({"baud": baud}));
}
