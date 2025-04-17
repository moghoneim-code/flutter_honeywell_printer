import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'flutter_honeywell_printer_method_channel.dart';

abstract class FlutterHoneywellPrinterPlatform extends PlatformInterface {
  /// Constructs a FlutterHoneywellPrinterPlatform.
  FlutterHoneywellPrinterPlatform() : super(token: _token);

  static final Object _token = Object();

  static FlutterHoneywellPrinterPlatform _instance = MethodChannelFlutterHoneywellPrinter();

  /// The default instance of [FlutterHoneywellPrinterPlatform] to use.
  ///
  /// Defaults to [MethodChannelFlutterHoneywellPrinter].
  static FlutterHoneywellPrinterPlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [FlutterHoneywellPrinterPlatform] when
  /// they register themselves.
  static set instance(FlutterHoneywellPrinterPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<String?> getPlatformVersion() {
    throw UnimplementedError('platformVersion() has not been implemented.');
  }
}
