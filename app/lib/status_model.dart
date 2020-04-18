import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:octo4a/backend/backend.dart';
import 'package:permission_handler/permission_handler.dart';

const BAUDRATES = [
  250000,
  115200,
  230400,
  57600,
  38400,
  19200,
  9600,
];

class StatusModel extends ChangeNotifier {
  StreamSubscription statusSubscription;

  List<String> _serialPorts = List();
  List<String> _cameraResolutions = List();
  OctoPrintStatus _octoPrintStatus;
  int _installationStatus = -1;
  bool _bootstrapInstalled;
  String _selectedPort;
  String _selectedResolution;
  int _selectedBaudrate;
  bool _deviceConnected = false;
  bool _isCameraServerStarted = false;
  String _ipAddress;

  bool get validatingBootstrap => _bootstrapInstalled == null;
  bool get isBootstrapInstalled => _bootstrapInstalled;
  String get selectedPort => _selectedPort;
  List<String> get serialPorts => _serialPorts;
  OctoPrintStatus get status => _octoPrintStatus;
  String get selectedResolution => _selectedResolution;
  List<String> get cameraResolutions => _cameraResolutions;
  int get installationStatus => _installationStatus;
  bool get isDeviceConnected => _serialPorts.isNotEmpty;
  bool get isCameraServerStarted => _isCameraServerStarted;
  int get selectedBaudrate => _selectedBaudrate;
  String get ipAddress => _ipAddress;

  StatusModel() {
    /*validateInstallationStatus().then((v) {
      _bootstrapInstalled = v;
      notifyListeners();
    });*/

    statusSubscription = statusSink.receiveBroadcastStream().listen(
      (data) {
        var parsedEvent = json.decode(data);
        print(parsedEvent);

        switch (parsedEvent["eventType"]) {
          case "installationStatus":
            _installationStatus++;
            notifyListeners();
            break;
          case "serverStatus":
            updateServerStatus(parsedEvent["body"]["status"]);
            if (_octoPrintStatus == OctoPrintStatus.RUNNING) {
              _ipAddress = parsedEvent["body"]["ipAddress"];
            }
            notifyListeners();
            break;
          case "cameraResolutions":
            _cameraResolutions = List<String>.from(parsedEvent["body"]["resolutions"]);
            _selectedResolution = parsedEvent["body"]["selected"];
            notifyListeners();
            break;
          case "deviceStatus":
            _deviceConnected = parsedEvent["status"] == "connected";
            notifyListeners();
            break;
          case "devicesList":
            print(data);
            print(parsedEvent["body"]["devices"]);
            _serialPorts = List<String>.from(parsedEvent["body"]["devices"])
                .map((e) => e == null ? "Unnamed port" : e)
                .toList();
            notifyListeners();
            break;
        }
      },
    );
    queryServerStatus();
    queryUsbDevices();
    queryCameraResolutions();
  }

  void fetchCameraResolutions() {
    queryCameraResolutions();
  }

  void queryUsbDevices() {
    fetchUsbDevices();
  }

  void changeResolution(String resolution) {
    _selectedResolution = resolution;
    setResolution(resolution);
    stopCamera();
    Future.delayed(Duration(milliseconds: 500), () {
      startCamera();
      // queryCameraResolutions();
    });

  }

  void connectusb() {
    selectSerialDevice(0);
  }

  void startCamera() async {
    if (await Permission.camera.request().isGranted) {
      startCameraServer();
      _isCameraServerStarted = true;
      notifyListeners();
    }
  }

  void stopCamera() {
    stopCameraServer();
    _isCameraServerStarted = false;
    notifyListeners();
  }

  void updateServerStatus(String status) {
    switch (status) {
      case "INSTALLING":
        _octoPrintStatus = OctoPrintStatus.INSTALLING;
        break;
      case "STARTING_UP":
        _octoPrintStatus = OctoPrintStatus.STARTING_UP;
        break;
      case "RUNNING":
        _octoPrintStatus = OctoPrintStatus.RUNNING;
        break;
      case "STOPPED":
        _octoPrintStatus = OctoPrintStatus.STOPPED;
        break;
    }

    notifyListeners();
  }

  @override
  void dispose() {
    statusSubscription.cancel();
    super.dispose();
  }
}
