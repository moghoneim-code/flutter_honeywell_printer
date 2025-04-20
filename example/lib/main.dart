import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'dart:async';

import 'package:flutter/services.dart';
import 'package:flutter_honeywell_printer/flutter_honeywell_printer.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  String _platformVersion = 'Unknown';
  final _flutterHoneywellPrinterPlugin = FlutterHoneywellPrinter();

  @override
  void initState() {
    super.initState();
    // initPlatformState();
  }

  // Platform messages are asynchronous, so we initialize in an async method.
  // Future<void> initPlatformState() async {
  //   String platformVersion;
  //   // Platform messages may fail, so we use a try/catch PlatformException.
  //   // We also handle the message potentially returning null.
  //   try {
  //     platformVersion =
  //         await FlutterHoneywellPrinter.getPlatformVersion() ?? 'Unknown platform version';
  //   } on PlatformException {
  //     platformVersion = 'Failed to get platform version.';
  //   }
  //
  //   // If the widget was removed from the tree while the asynchronous platform
  //   // message was in flight, we want to discard the reply rather than calling
  //   // setState to update our non-existent appearance.
  //   if (!mounted) return;
  //
  //   setState(() {
  //     _platformVersion = platformVersion;
  //   });
  // }

  Future<String?> _pickFile()async{

    final result = await FilePicker.platform.pickFiles(
      type: FileType.custom,
      allowedExtensions: ['pdf'],
    );
    if (result == null) return null;
    final path = result.files.single.path;
    return path;
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        floatingActionButton: FloatingActionButton(onPressed: () async {
      FlutterHoneywellPrinter.connectPrinter("DC:1D:30:92:DA:8E");
      /// save this file in your device storage
      /// and then use the path to print
final path = await _pickFile();
      FlutterHoneywellPrinter.printPdf(path!);
       // print(result);
        }),
        appBar: AppBar(
          title: const Text('Plugin example app'),
        ),
        body: Center(
          child: Text('Running on: $_platformVersion\n'),
        ),
      ),
    );
  }
}
