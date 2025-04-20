import 'dart:io';
import 'package:device_info_plus/device_info_plus.dart';
import 'package:flutter/material.dart';
import 'package:permission_handler/permission_handler.dart';

class BluetoothPermissionHelper {
  /// Request all required Bluetooth permissions based on platform and Android version
  static Future<bool> requestBluetoothPermissions() async {
    if (!Platform.isAndroid) {
      // iOS or other platforms may have different permission requirements
      return true;
    }

    // For Android
    List<Permission> permissions = [];

    // Basic Bluetooth permissions
    permissions.add(Permission.bluetooth);

    // Check Android version for additional permissions
    if (Platform.isAndroid) {
      // Android API level check
      final androidInfo = await DeviceInfoPlugin().androidInfo;
      final sdkInt = androidInfo.version.sdkInt;

      // For Android 6.0+ (API 23+)
      if (sdkInt >= 23) {
        permissions.add(Permission.location);
      }

      // For Android 12+ (API 31+)
      if (sdkInt >= 31) {
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

  /// Show permission dialog if permissions are denied
  static Future<void> showPermissionDialog(BuildContext context) async {
    return showDialog(
      context: context,
      builder: (BuildContext context) {
        return AlertDialog(
          title: Text("Bluetooth Permissions Required"),
          content: Text(
              "This app needs Bluetooth permissions to connect to the printer. "
                  "Please grant these permissions in the app settings."
          ),
          actions: [
            TextButton(
              child: Text("Cancel"),
              onPressed: () {
                Navigator.of(context).pop();
              },
            ),
            TextButton(
              child: Text("Open Settings"),
              onPressed: () {
                Navigator.of(context).pop();
                openAppSettings();
              },
            ),
          ],
        );
      },
    );
  }
}