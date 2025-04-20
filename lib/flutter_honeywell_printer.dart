import 'dart:async';
import 'dart:io';

import 'package:device_info_plus/device_info_plus.dart';
import 'package:flutter/services.dart';
import 'package:permission_handler/permission_handler.dart';

class FlutterHoneywellPrinter {
  static const MethodChannel _channel = MethodChannel('flutter_honeywell_printer');

  /// Check if a printer is currently connected
  static Future<bool> get isPrinterConnected async {
    try {
      final bool result = await _channel.invokeMethod('isPrinterConnected');
      return result;
    } on PlatformException catch (e) {
      print('Error checking printer connection: ${e.message}');
      return false;
    }
  }

  /// Get the last connection error message
  static Future<String> get lastConnectionError async {
    try {
      final String errorMsg = await _channel.invokeMethod('getLastConnectionError');
      return errorMsg;
    } on PlatformException catch (e) {
      return "Error retrieving connection status: ${e.message}";
    }
  }

  /// Check and request Bluetooth permissions
  static Future<bool> checkAndRequestPermissions() async {
    if (!Platform.isAndroid) {
      return true; // iOS handles permissions differently
    }

    // Android permissions
    List<Permission> permissions = [
      Permission.bluetooth,
    ];

    // Add permissions based on Android version
    if (Platform.isAndroid) {
      final androidInfo = await DeviceInfoPlugin().androidInfo;
      final sdkInt = androidInfo.version.sdkInt;

      if (sdkInt >= 23) { // Android 6.0+
        permissions.add(Permission.location);
      }

      if (sdkInt >= 31) { // Android 12+
        permissions.add(Permission.bluetoothConnect);
        permissions.add(Permission.bluetoothScan);
      }
    }

    // Request all permissions
    Map<Permission, PermissionStatus> statuses = await permissions.request();

    // Check if all permissions are granted
    bool allGranted = true;
    statuses.forEach((permission, status) {
      if (status != PermissionStatus.granted) {
        allGranted = false;
      }
    });

    return allGranted;
  }

  /// Connect to a printer using its MAC address
  ///
  /// Returns true if connection was successful
  /// Throws PlatformException if connection fails or permissions are denied
  static Future<bool> connectPrinter(String macAddress) async {
    if (macAddress == null || macAddress.isEmpty) {
      throw PlatformException(
          code: 'INVALID_ARGUMENT',
          message: 'MAC address is required'
      );
    }

    // Check permissions first
    bool permissionsGranted = await checkAndRequestPermissions();
    if (!permissionsGranted) {
      throw PlatformException(
          code: 'PERMISSION_DENIED',
          message: 'Bluetooth permissions are required to connect to the printer'
      );
    }

    try {
      final bool result = await _channel.invokeMethod('connectPrinter', {'macAddress': macAddress});
      return result;
    } on PlatformException {
      // Let the original exception propagate to caller
      rethrow;
    }
  }

  /// Disconnect from the currently connected printer
  static Future<bool> disconnectPrinter() async {
    try {
      final bool result = await _channel.invokeMethod('disconnectPrinter');
      return result;
    } on PlatformException catch (e) {
      print('Error disconnecting printer: ${e.message}');
      return false;
    }
  }

  /// Print a PDF from a file path
  ///
  /// [pdfPath] - The path to the PDF file in local storage
  /// [withGap] - Whether to print with a gap between pages (default: false)
  ///
  /// Returns true if printing was successful
  /// Throws PlatformException if printing fails
  static Future<bool> printPdf(String pdfPath, {bool withGap = false}) async {
    // Check if file exists
    final file = File(pdfPath);
    if (!await file.exists()) {
      throw PlatformException(
          code: 'FILE_NOT_FOUND',
          message: 'PDF file not found at path: $pdfPath'
      );
    }

    // Check if printer is connected
    if (!await isPrinterConnected) {
      throw PlatformException(
          code: 'PRINTER_NOT_CONNECTED',
          message: 'Printer is not connected'
      );
    }

    try {
      final bool result = await _channel.invokeMethod('printPdf', {
        'pdfPath': pdfPath,
        'withGap': withGap,
      });
      return result;
    } on PlatformException {
      // Let the original exception propagate to caller
      rethrow;
    }
  }
}