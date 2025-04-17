package com.dbs.flutter_honeywell_printer;

import android.content.Context;
import androidx.annotation.NonNull;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;

/** FlutterHoneywellPrinterPlugin */
public class FlutterHoneywellPrinterPlugin implements FlutterPlugin, MethodCallHandler {
  /// The MethodChannel that will handle communication between Flutter and native Android
  private MethodChannel channel;
  private Context context;
  private HoneywellPrinterManager printerManager;

  @Override
  public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
    channel = new MethodChannel(flutterPluginBinding.getBinaryMessenger(), "flutter_honeywell_printer");
    channel.setMethodCallHandler(this);
    context = flutterPluginBinding.getApplicationContext();
    printerManager = new HoneywellPrinterManager(context);
  }

  @Override
  public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
    switch (call.method) {
      case "connectPrinter":
        String macAddress = call.argument("macAddress");
        if (macAddress == null) {
          result.error("INVALID_ARGUMENT", "MAC address is required", null);
          return;
        }

        printerManager.connectPrinter(macAddress, new HoneywellPrinterManager.PrinterCallback() {
          @Override
          public void onResult(boolean success, String message) {
            if (success) {
              result.success(true);
            } else {
              result.error("CONNECTION_FAILED", message, null);
            }
          }
        });
        break;

      case "isPrinterConnected":
        result.success(printerManager.isPrinterConnected());
        break;

      case "printPdf":
        String pdfPath = call.argument("pdfPath");
        boolean withGap = call.argument("withGap") != null ? call.argument("withGap") : false;

        if (pdfPath == null) {
          result.error("INVALID_ARGUMENT", "PDF path is required", null);
          return;
        }

        if (!printerManager.isPrinterConnected()) {
          result.error("PRINTER_NOT_CONNECTED", "Printer is not connected", null);
          return;
        }

        printerManager.printPdf(pdfPath, withGap, new HoneywellPrinterManager.PrinterCallback() {
          @Override
          public void onResult(boolean success, String message) {
            if (success) {
              result.success(true);
            } else {
              result.error("PRINT_FAILED", message, null);
            }
          }
        });
        break;

      default:
        result.notImplemented();
        break;
    }
  }

  @Override
  public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
    channel.setMethodCallHandler(null);
    printerManager.disconnectPrinter();
    printerManager = null;
  }
}