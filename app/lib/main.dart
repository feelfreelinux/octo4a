import 'package:camera/camera.dart';
import 'package:flutter/material.dart';
import 'package:octo4a/backend/backend.dart';
import 'package:octo4a/screens/installation.dart';
import 'package:octo4a/screens/landing.dart';
import 'package:octo4a/screens/status.dart';
import 'package:octo4a/status_model.dart';
import 'package:provider/provider.dart';

List<CameraDescription> cameras;

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  await availableCameras();
  runApp(MyApp());
}

class MyApp extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return ChangeNotifierProvider<StatusModel>(
      create: (context) => StatusModel(),
      child: MaterialApp(
        title: 'Flutter Demo',
        theme: ThemeData(
          primarySwatch: Colors.blue,
        ),
        home: ChangeNotifierProvider<StatusModel>(
          create: (context) => StatusModel(),
          child: FutureBuilder<bool>(
            future: validateInstallationStatus(),
            builder: (context, snapshot) {
              if (!snapshot.hasData) {
                return Scaffold(
                    body: Center(child: CircularProgressIndicator()));
              }
              if (snapshot.data) {
                return StatusScreen();
              }
              return LandingPage();
            },
          ),
        ),
      ),
    );
  }
}
