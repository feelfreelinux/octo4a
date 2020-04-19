import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:octo4a/backend/backend.dart';
import 'package:octo4a/widgets/widgets.dart';
import 'package:octo4a/status_model.dart';
import 'package:provider/provider.dart';

class StatusData {
  final Color textColor;
  final String subtitle;
  final String description;
  final Widget icon;
  StatusData({this.textColor, this.subtitle, this.description, this.icon});
}

class StatusScreen extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        backgroundColor: Colors.white,
        title: Text(
          "OctoPrint on Android",
          style: TextStyle(color: Colors.black),
        ),
        centerTitle: true,
      ),
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
            child: SingleChildScrollView(
              child: Center(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: <Widget>[
                    Padding(
                      padding:
                          const EdgeInsets.only(top: 22.0, left: 22, right: 22),
                      child: _drawServerStatus(context, model),
                    ),
                    Padding(
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
                        child: Padding(
                          padding: const EdgeInsets.only(bottom: 16.0),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: <Widget>[
                              Text(
                                "Please connect your printer to the phone with OTG cable, and hit refresh button to detect your printer.",
                                style: TextStyle(
                                    fontSize: 15, fontWeight: FontWeight.w300),
                              ),
                              model.isDeviceConnected
                                  ? Padding(
                                      padding: const EdgeInsets.only(top: 8.0),
                                      child: Text("Device connected on: " +
                                          model.serialPorts.first))
                                  : Container(),
                            ],
                          ),
                        ),
                      ),
                    ),
                    Padding(
                      padding:
                          const EdgeInsets.only(top: 22.0, left: 22, right: 22),
                      child: PanelCard(
                        trailling: IconButton(
                          padding: EdgeInsets.zero,
                          icon: Icon(
                              model.isCameraServerStarted
                                  ? Icons.stop
                                  : Icons.play_arrow,
                              color: Colors.green),
                          onPressed: () => {
                            if (model.isCameraServerStarted)
                              {model.stopCamera()}
                            else
                              {model.startCamera()}
                          },
                        ),
                        textColor: model.isCameraServerStarted
                            ? Colors.green
                            : Colors.red,
                        title: model.isCameraServerStarted
                            ? "Camera server running"
                            : "Camera server stopped",
                        child: Padding(
                          padding: const EdgeInsets.only(bottom: 16.0, top: 8),
                          child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  "Your device's camera will provide the octoprint instance with live video preview.",
                                ),
                                Padding(
                                  padding: const EdgeInsets.only(top: 14.0),
                                  child: Text("Camera resolution"),
                                ),
                                Row(
                                  children: [
                                    new DropdownButton<String>(
                                      value: model.selectedResolution,
                                      items:
                                          model.cameraResolutions.map((value) {
                                        return new DropdownMenuItem<String>(
                                          value: value,
                                          child: new Text(value),
                                        );
                                      }).toList(),
                                      onChanged: (v) {
                                        model.changeResolution(v);
                                      },
                                    ),
                                  ],
                                ),
                              ]),
                        ),
                      ),
                    ),
                    Padding(
                      padding:
                          const EdgeInsets.only(top: 22.0, left: 22, right: 22),
                      child: PanelCard(
                        title: "Happy printing!",
                        child: Padding(
                            padding:
                                const EdgeInsets.only(bottom: 16.0, top: 8),
                            child: Text(
                                "In order to connect to your printer in OctoPrint, select \"AUTO\" as a serial port, and appropriate baudrate. Remember that this is an early version of this app, so stuff like automatic baudrate detection might not work.")),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _drawServerStatus(BuildContext context, StatusModel model) {
    return PanelCard(
      title: getStatusData(model).subtitle,
      trailling: getStatusData(model).icon,
      textColor: getStatusData(model).textColor,
      child: Padding(
        padding: EdgeInsets.only(
            bottom: model.status == OctoPrintStatus.RUNNING ? 8.0 : 16),
        child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              Text(getStatusData(model).description),
              model.status == OctoPrintStatus.RUNNING
                  ? InkWell(
                      onTap: () {
                        Clipboard.setData(ClipboardData(text: model.ipAddress));

                        final snackBar =
                            SnackBar(content: Text('Address copied'));

                        Scaffold.of(context).showSnackBar(snackBar);
                      },
                      child: Padding(
                        padding: const EdgeInsets.only(top: 8.0, bottom: 8),
                        child: Center(
                            child: Text(
                          model.ipAddress,
                          style: TextStyle(
                            color: Colors.blueAccent,
                            fontWeight: FontWeight.w500,
                            fontSize: 17,
                          ),
                        )),
                      ),
                    )
                  : Container()
            ]),
      ),
    );
  }

  StatusData getStatusData(StatusModel model) {
    Color textColor;
    String subtitle;
    String description;
    Widget icon = IconButton(
      padding: EdgeInsets.zero,
      icon: Icon(Icons.font_download, color: Colors.black.withOpacity(0)),
      onPressed: () {},
    );
    switch (model.status) {
      case OctoPrintStatus.INSTALLING:
        textColor = Colors.orange;
        description = "Installation might take a while";
        subtitle = "OctoPrint server installing";
        break;
      case OctoPrintStatus.RUNNING:
        textColor = Colors.green;
        description =
            "Use this address to access this printer from anywhere in this network";
        icon = IconButton(
          padding: EdgeInsets.zero,
          icon: Icon(Icons.stop, color: Colors.red),
          onPressed: () => stopServer(),
        );
        subtitle = "OctoPrint server running";
        break;
      case OctoPrintStatus.STOPPED:
        textColor = Colors.red;
        subtitle = "OctoPrint server stopped";
        description = "Printing server is currently stopped.";
        icon = IconButton(
          padding: EdgeInsets.zero,
          icon: Icon(Icons.play_arrow, color: Colors.green),
          onPressed: () => startServer(),
        );
        break;
      case OctoPrintStatus.STARTING_UP:
        textColor = Colors.blue;
        icon = Container(
          margin: EdgeInsets.all(16.0),
          width: 20,
          height: 20,
          child: CircularProgressIndicator(
            strokeWidth: 3,
          ),
        );
        subtitle = "OctoPrint server starting";
        description =
            "The IP address will be provided once the printing server starts.";
        break;
    }

    return StatusData(
      textColor: textColor ?? Colors.pink,
      description: description ?? "",
      icon: icon ?? Container(),
      subtitle: subtitle ?? "empty",
    );
  }
}
