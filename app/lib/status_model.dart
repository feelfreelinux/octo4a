import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:octo4a/backend/backend.dart';

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
  OctoPrintStatus _octoPrintStatus;
  int _installationStatus = -1;
  bool _bootstrapInstalled;
  String _selectedPort;
  int _selectedBaudrate;
  bool _deviceConnected = false;
  String _ipAddress;
  
  bool get validatingBootstrap => _bootstrapInstalled == null;
  bool get isBootstrapInstalled => _bootstrapInstalled;
  String get selectedPort => _selectedPort;
  List<String> get serialPorts => _serialPorts;
  OctoPrintStatus get status => _octoPrintStatus;
  int get installationStatus => _installationStatus;
  bool get isDeviceConnected => _serialPorts.isNotEmpty;
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
          case "deviceStatus":
            _deviceConnected = parsedEvent["status"] == "connected";
            notifyListeners();
            break;
          case "devicesList":
            print(data);
            print(parsedEvent["body"]["devices"]);
            _serialPorts = List<String>.from(parsedEvent["body"]["devices"]).map((e) => e == null ? "Unnamed port" : e).toList();
            notifyListeners();
            break;
        }
      },
    );
    queryServerStatus();
    queryUsbDevices();
  }

  void queryUsbDevices() {
    fetchUsbDevices();
  }

  void connectusb() {
    selectSerialDevice(0);
    
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
