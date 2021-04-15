import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class NetworkingPage extends StatefulWidget {
  @override
  _NetworkingPageState createState() => _NetworkingPageState();
}

class _NetworkingPageState extends State<NetworkingPage> {
  static const platform = const MethodChannel('NETWORK_INFORMATION');
  StreamSubscription<dynamic> subscription;

  Map<dynamic, dynamic> _deviceData = <dynamic, dynamic>{};

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: <Widget>[
          Center(
            child: FlatButton(
              onPressed: () => this.subscription == null
                  ? getDeviceInfo()
                  : stopSubscription(),
              child: Text(
                this.subscription == null
                    ? "Capturar Dados do dispositivo"
                    : "Parar Captura",
                style: TextStyle(color: Colors.white),
              ),
              color: this.subscription == null
                  ? Theme.of(context).primaryColorDark
                  : Colors.red,
            ),
          ),
          ..._deviceData.keys.map((dynamic property) {
            return InfoItem(
              title: property.toString(),
              data: _deviceData[property],
              icon: Icons.wifi,
            );
          }).toList()
        ],
      ),
    );
  }

  void getDeviceInfo() async {
    Map<dynamic, dynamic> data = _deviceData;
    const EventChannel _stream = EventChannel('NETWORK_INFORMATION');

    this.subscription = _stream.receiveBroadcastStream().listen((event) {
      print("EVENT CHANGED $event");
      data.addAll(event);

      setState(() {
        _deviceData = data;
      });
    });
  }

  void stopSubscription() {
    this.subscription.cancel();

    setState(() {
      this.subscription = null;
    });
  }
}

class InfoItem extends StatelessWidget {
  final IconData icon;
  final String title;
  final String data;

  InfoItem({this.icon, @required this.title, this.data})
      : assert(title != null);

  @override
  Widget build(BuildContext context) {
    return ListTile(
      leading: Icon(icon),
      title: Text(title),
      subtitle: Text(data),
    );
  }
}
