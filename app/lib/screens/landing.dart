import 'package:flutter/material.dart';
import 'package:octo4a/screens/installation.dart';
import 'package:octo4a/utils.dart';
import 'package:octo4a/widgets/widgets.dart';
import 'package:permission_handler/permission_handler.dart';

class LandingPage extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Container(
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
                  padding: const EdgeInsets.all(22.0),
                  child: Container(
                      width: 150,
                      height: 150,
                      child: Image.network(
                          "https://octoprint.org/assets/img/logo.png")),
                ),
                Padding(
                  padding:
                      const EdgeInsets.only(top: 00.0, left: 22, right: 22),
                  child: PanelCard(
                    title: "No existing installation detected",
                    child: Padding(
                      padding: const EdgeInsets.only(top: 8.0, bottom: 16.0),
                      child: Text(
                        "This application will install OctoPrint server on your android device. Please make sure that you have at least 700mb of storage available. You will also need an OTG cable in order to connect your phone to your 3d printer.\n\nThis project is an unofficial product, not affiliated with OctoPrint project.",
                        style: TextStyle(
                            fontSize: 17, fontWeight: FontWeight.w300),
                      ),
                    ),
                  ),
                ),
                Padding(
                  padding: const EdgeInsets.only(top: 22.0),
                  child: FlatButton(
                    onPressed: () async {
                      await Permission.storage.request().isGranted;
                      Navigator.of(context).pushReplacement(createRoute(InstallationScreen()));
                    },
                    child: Text(
                      "Install OctoPrint",
                      style: TextStyle(
                          color: Colors.white,
                          fontSize: 18,
                          fontWeight: FontWeight.w700),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
