import 'dart:async';
import 'dart:io';

import 'package:flutter/services.dart';

class FlutterHoneywellPrinter {
  static const MethodChannel _channel = MethodChannel('flutter_honeywell_printer');

  /// Check if a printer is currently connected
  static Future<bool> get isPrinterConnected async {
    return await _channel.invokeMethod('isPrinterConnected');
  }

  /// Connect to a printer using its MAC address
  static Future<bool> connectPrinter(String macAddress) async {
    return await _channel.invokeMethod('connectPrinter', {'macAddress': macAddress});
  }

  /// Disconnect from the currently connected printer
  static Future<bool> disconnectPrinter() async {
    return await _channel.invokeMethod('disconnectPrinter');
  }

  /// Print a PDF from a file path
  /// 
  /// [pdfPath] - The path to the PDF file in local storage
  /// [withGap] - Whether to print with a gap between pages (default: false)
  static Future<bool> printPdf(String pdfPath, {bool withGap = false}) async {
    // Check if file exists
    final file = File(pdfPath);
    if (!await file.exists()) {
      throw FileSystemException('File not found', pdfPath);
    }

    return await _channel.invokeMethod('printPdf', {
      'pdfPath': pdfPath,
      'withGap': withGap,
    });
  }
}