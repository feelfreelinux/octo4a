import 'package:flutter/material.dart';
import 'package:octo4a/backend/backend.dart';
import 'package:octo4a/widgets/widgets.dart';
import 'package:octo4a/status_model.dart';
import 'package:provider/provider.dart';

class StatusData {
  final Color textColor;
  final String subtitle;
  StatusData({this.textColor, this.subtitle});
}

class StatusScreen extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Consumer<StatusModel>(
        builder: (context, model, _) => Container(
          decoration: BoxDecoration(
            gradient: LinearGradient(
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
              colors: [const Color(0xff00b09b), const Color(0xff96c93d)],
            ),
          ),
          child: SafeArea(
            child: Center(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: <Widget>[
                  Padding(
                    padding:
                        const EdgeInsets.only(top: 22.0, left: 22, right: 22),
                    child: PanelCard(
                      title: getStatusData(model.status).subtitle,
                      textColor: getStatusData(model.status).textColor,
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: <Widget>[
                          model.status == OctoPrintStatus.RUNNING
                              ? RichText(
                                  text: TextSpan(
                                      children: [
                                        TextSpan(
                                            text:
                                                "OctoPrint server is running in the background. Happy printing!\nIP Address: "),
                                        TextSpan(
                                            text: model.ipAddress,
                                            style:
                                                TextStyle(color: Colors.blue)),
                                      ],
                                      style: TextStyle(
                                          fontSize: 15,
                                          fontWeight: FontWeight.w300,
                                          color: Colors.black)),
                                )
                              : Container(),
                          Text(
                            "\n!!! Closing the server will stop all active prints !!!",
                            style: TextStyle(
                                fontSize: 15, fontWeight: FontWeight.w500),
                          ),
                          model.status == OctoPrintStatus.RUNNING ||
                                  model.status == OctoPrintStatus.STOPPED
                              ? FlatButton(
                                  padding: EdgeInsets.zero,
                                  onPressed: () {
                                    if (model.status ==
                                        OctoPrintStatus.RUNNING) {
                                      stopServer();
                                      return;
                                    }
                                    startServer();
                                  },
                                  child: Text(
                                    model.status == OctoPrintStatus.RUNNING
                                        ? "Stop the server"
                                        : "Start the server",
                                    style: TextStyle(
                                        color: model.status ==
                                                OctoPrintStatus.RUNNING
                                            ? Colors.red
                                            : Colors.green),
                                  ),
                                )
                              : Padding(
                                  padding: const EdgeInsets.all(8.0),
                                  child: Container(),
                                )
                        ],
                      ),
                    ),
                  ),
                  AnimatedOpacity(
                    duration: Duration(milliseconds: 500),
                    opacity: model.status == OctoPrintStatus.RUNNING ? 1 : 0.6,
                    child: Padding(
                      padding:
                          const EdgeInsets.only(top: 22.0, left: 22, right: 22),
                      child: PanelCard(
                        title: model.isDeviceConnected
                            ? "Printer is connected"
                            : "Printer not connected",
                        trailling: IconButton(
                          padding: EdgeInsets.zero,
                          icon: Icon(Icons.refresh),
                          onPressed: () {
                            model.queryUsbDevices();
                          },
                        ),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: <Widget>[
                            Padding(
                              padding: const EdgeInsets.only(top: 8.0),
                              child: Text(
                                "Please connect your printer to the phone with OTG cable, and hit refresh button to detect your printer.",
                                style: TextStyle(
                                    fontSize: 15, fontWeight: FontWeight.w300),
                              ),
                            ),
                            Padding(
                                padding:
                                    const EdgeInsets.only(top: 8.0, bottom: 8),
                                child: Text(model.serialPorts.isEmpty
                                    ? "No device connected"
                                    : model.serialPorts.first)),
                            Padding(
                              padding: const EdgeInsets.only(bottom: 8.0),
                              child: Text(
                                "Appropriate baudrate needs to be also set separately in OctoPrint interface",
                                style: TextStyle(
                                    fontSize: 14, fontWeight: FontWeight.w500),
                              ),
                            ),
                            DropdownButton<int>(
                              value: model.selectedBaudrate,
                              onChanged: (v) => model.selectBaudrate(v),
                              hint: Text("Select baudrate"),
                              items: BAUDRATES
                                  .map(
                                    (e) => DropdownMenuItem(
                                      value: e,
                                      child: Text(e.toString()),
                                    ),
                                  )
                                  .toList(),
                            ),
                            FlatButton(
                              padding: EdgeInsets.zero,
                              child: Text(model.isDeviceConnected
                                  ? "Reconnect the printer"
                                  : "Connect to your printer"),
                              onPressed: () {
                                model.connectusb();
                              },
                            )
                          ],
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  StatusData getStatusData(OctoPrintStatus status) {
    Color textColor;
    String subtitle;
    switch (status) {
      case OctoPrintStatus.INSTALLING:
        textColor = Colors.orange;
        subtitle = "OctoPrint server installing";
        break;
      case OctoPrintStatus.RUNNING:
        textColor = Colors.green;
        subtitle = "OctoPrint server running";
        break;
      case OctoPrintStatus.STOPPED:
        textColor = Colors.red;
        subtitle = "OctoPrint server stopped";
        break;
      case OctoPrintStatus.STARTING_UP:
        textColor = Colors.blue;
        subtitle = "OctoPrint server starting";
        break;
    }

    return StatusData(
      textColor: textColor ?? Colors.pink,
      subtitle: subtitle ?? "empty",
    );
  }
}
