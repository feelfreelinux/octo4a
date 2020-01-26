import 'package:flutter/material.dart';

class PanelCard extends StatelessWidget {
  final Widget child;
  final String title;
  final Color textColor;
  final Widget trailling;

  PanelCard(
      {@required this.child,
      @required this.title,
      this.textColor = Colors.black,
      this.trailling});
  @override
  Widget build(BuildContext context) {
    return Card(
      elevation: 4.0,
      child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            AnimatedSwitcher(
              duration: Duration(milliseconds: 500),
              child: Row(
              children: [
                Padding(
                  padding: EdgeInsets.only(left: 16, top: trailling == null ? 16 : 0),
                  child: Text(
                    title,
                    style: TextStyle(
                        fontSize: 20,
                        color: textColor,
                        fontWeight: FontWeight.w600),
                  ),
                ),
                Expanded(
                  child: Container(),
                ),
                trailling ?? Container()
              ],
            )),
            Padding(
              padding: const EdgeInsets.only(top: 16.0, right: 16, left: 16),
              child: child,
            ),
          ]),
    );
  }
}
