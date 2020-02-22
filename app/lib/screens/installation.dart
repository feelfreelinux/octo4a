
import 'package:flutter/material.dart';
import 'package:octo4a/backend/backend.dart';
import 'package:octo4a/screens/status.dart';
import 'package:octo4a/status_model.dart';
import 'package:octo4a/utils.dart';
import 'package:octo4a/widgets/widgets.dart';
import 'package:provider/provider.dart';

enum StepStatus {
  DONE,
  INPROGRESS,
  PENDING,
}

class InstallationScreen extends StatefulWidget {
  @override
  _InstallationScreenState createState() => _InstallationScreenState();
}

class _InstallationScreenState extends State<InstallationScreen> {
  var steps = [
    "Installing bootstrap (aarch64)...",
    "Downloading OctoPrint (develop)",
    "Installing dependencies...",
    "Booting OctoPrint for the first time...",
  ];

  @override
  void initState() {
    beginInstallation();
    super.initState();
  }

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
                crossAxisAlignment: CrossAxisAlignment.center,
                children: <Widget>[
                  Padding(
                    padding: const EdgeInsets.only(top: 22.0),
                    child: Text(
                      (((model.installationStatus) / (steps.length)) * 100)
                              .round()
                              .toString() +
                          "%",
                      style: TextStyle(
                          fontSize: 62,
                          fontWeight: FontWeight.w700,
                          color: Colors.white),
                    ),
                  ),
                  Padding(
                    padding: const EdgeInsets.all(22.0),
                    child: PanelCard(
                      title: "Installing OctoPrint...",
                      child: Padding(
                        padding: const EdgeInsets.only(bottom: 12.0),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: <Widget>[
                            Padding(
                              padding:
                                  const EdgeInsets.only(top: 8.0, bottom: 8),
                              child: Text(
                                "This might take a while depending on your network connection. (up to 30 minutes!)",
                                style: TextStyle(
                                    fontSize: 17, fontWeight: FontWeight.w300),
                              ),
                            ),
                            ...steps
                                .map(
                                  (step) => Column(
                                    crossAxisAlignment:
                                        CrossAxisAlignment.start,
                                    children: <Widget>[
                                      Divider(),
                                      Row(
                                        children: [
                                          _drawStepText(
                                              step,
                                              getStatusForStep(step,
                                                  model.installationStatus),
                                              model.installationStatus),
                                          Expanded(
                                            child: Container(),
                                          ),
                                          AnimatedContainer(
                                            duration:
                                                Duration(milliseconds: 200),
                                            child: model.installationStatus <
                                                    steps.indexOf(step)
                                                ? Container()
                                                : (model.installationStatus ==
                                                        steps.indexOf(step)
                                                    ? Container(
                                                        width: 15,
                                                        height: 15,
                                                        child:
                                                            CircularProgressIndicator(
                                                          strokeWidth: 2,
                                                        ))
                                                    : Icon(Icons.check,
                                                        color: Colors.green)),
                                          )
                                        ],
                                      ),
                                    ],
                                  ),
                                )
                                .toList(),
                            Divider(),
                            _drawStepText(
                                "Installation complete",
                                model.installationStatus == steps.length
                                    ? StepStatus.DONE
                                    : StepStatus.PENDING,
                                model.installationStatus)
                          ],
                        ),
                      ),
                    ),
                  ),
                  Padding(
                    padding: const EdgeInsets.only(top: 8.0),
                    child: AnimatedOpacity(
                      duration: Duration(milliseconds: 500),
                      opacity:
                          model.installationStatus == steps.length ? 1 : 0.4,
                      child: FlatButton(
                        onPressed: () {
                          if (model.installationStatus < steps.length) return;
                          Navigator.of(context)
                              .pushReplacement(createRoute(StatusScreen()));
                        },
                        child: Text(
                          "Continue",
                          style: TextStyle(
                              color: Colors.white,
                              fontSize: 18,
                              fontWeight: FontWeight.w700),
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

  void openStatusScreen() {
    Navigator.of(context).pushReplacement(createRoute(StatusScreen()));
  }

  StepStatus getStatusForStep(String step, int currentStep) {
    if (currentStep > steps.indexOf(step)) {
      return StepStatus.DONE;
    } else if (currentStep == steps.indexOf(step)) {
      return StepStatus.INPROGRESS;
    }

    return StepStatus.PENDING;
  }

  Widget _drawStepText(String text, StepStatus status, int currentStep) {
    return AnimatedOpacity(
      duration: Duration(milliseconds: 500),
      opacity: status == StepStatus.PENDING ? 0.4 : 1,
      child: Container(
          key: ValueKey(currentStep),
          child: Padding(
            padding: const EdgeInsets.only(top: 8.0, bottom: 8),
            child: Text(
              text,
              style: TextStyle(
                  fontSize: 15,
                  color:
                      status == StepStatus.DONE ? Colors.green : Colors.black,
                  fontWeight: FontWeight.w400),
            ),
          )),
    );
  }
}
