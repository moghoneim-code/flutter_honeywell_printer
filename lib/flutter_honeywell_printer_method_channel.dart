import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'flutter_honeywell_printer_platform_interface.dart';

/// An implementation of [FlutterHoneywellPrinterPlatform] that uses method channels.
class MethodChannelFlutterHoneywellPrinter extends FlutterHoneywellPrinterPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('flutter_honeywell_printer');

  @override
  Future<String?> getPlatformVersion() async {
    final version = await methodChannel.invokeMethod<String>('getPlatformVersion');
    return version;
  }
}
