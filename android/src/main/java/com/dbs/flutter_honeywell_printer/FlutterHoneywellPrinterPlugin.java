package com.dbs.flutter_honeywell_printer;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.NonNull;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;

/** FlutterHoneywellPrinterPlugin */
public class FlutterHoneywellPrinterPlugin implements FlutterPlugin, MethodCallHandler {
  private static final String TAG = "HoneywellPrinterPlugin";
  /// The MethodChannel that will handle communication between Flutter and native Android
  private MethodChannel channel;
  private Context context;
  private HoneywellPrinterManager printerManager;
  private Handler mainHandler;

  @Override
  public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
    channel = new MethodChannel(flutterPluginBinding.getBinaryMessenger(), "flutter_honeywell_printer");
    channel.setMethodCallHandler(this);
    context = flutterPluginBinding.getApplicationContext();
    printerManager = new HoneywellPrinterManager(context);
    mainHandler = new Handler(Looper.getMainLooper());

    Log.d(TAG, "Plugin attached to Flutter engine");
  }

  @Override
  public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
    Log.d(TAG, "Method call received: " + call.method);

    switch (call.method) {
      case "connectPrinter":
        String macAddress = call.argument("macAddress");
        if (macAddress == null || macAddress.isEmpty()) {
          Log.e(TAG, "Invalid MAC address provided");
          result.error("INVALID_ARGUMENT", "MAC address is required", null);
          return;
        }

        Log.d(TAG, "Connecting to printer: " + macAddress);
        printerManager.connectPrinter(macAddress, new HoneywellPrinterManager.PrinterCallback() {
          @Override
          public void onResult(boolean success, String message) {
            // Ensure callback happens on main thread
            mainHandler.post(() -> {
              if (success) {
                Log.d(TAG, "Printer connected successfully: " + message);
                result.success(true);
              } else {
                Log.e(TAG, "Printer connection failed: " + message);
                result.error("CONNECTION_FAILED", message, null);
              }
            });
          }
        });
        break;

      case "isPrinterConnected":
        boolean connected = printerManager.isPrinterConnected();
        Log.d(TAG, "Printer connected status: " + connected);
        result.success(connected);
        break;

      case "getLastConnectionError":
        String errorMsg = printerManager.getLastConnectionError();
        Log.d(TAG, "Last connection error: " + (errorMsg != null ? errorMsg : "None"));
        result.success(errorMsg != null ? errorMsg : "");
        break;

      case "printPdf":
        String pdfPath = call.argument("pdfPath");
        Boolean withGapArg = call.argument("withGap");
        boolean withGap = withGapArg != null ? withGapArg : false;

        if (pdfPath == null || pdfPath.isEmpty()) {
          Log.e(TAG, "Invalid PDF path provided");
          result.error("INVALID_ARGUMENT", "PDF path is required", null);
          return;
        }

        if (!printerManager.isPrinterConnected()) {
          Log.e(TAG, "Cannot print - printer not connected");
          result.error("PRINTER_NOT_CONNECTED", "Printer is not connected", null);
          return;
        }

        Log.d(TAG, "Printing PDF: " + pdfPath + ", withGap: " + withGap);
        printerManager.printPdf(pdfPath, withGap, new HoneywellPrinterManager.PrinterCallback() {
          @Override
          public void onResult(boolean success, String message) {
            // Ensure callback happens on main thread
            mainHandler.post(() -> {
              if (success) {
                Log.d(TAG, "PDF printed successfully");
                result.success(true);
              } else {
                Log.e(TAG, "PDF printing failed: " + message);
                result.error("PRINT_FAILED", message, null);
              }
            });
          }
        });
        break;

      case "disconnectPrinter":
        Log.d(TAG, "Disconnecting printer");
        printerManager.disconnectPrinter();
        result.success(true);
        break;

      default:
        Log.w(TAG, "Method not implemented: " + call.method);
        result.notImplemented();
        break;
    }
  }

  @Override
  public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
    Log.d(TAG, "Plugin detached from Flutter engine");
    channel.setMethodCallHandler(null);
    printerManager.disconnectPrinter();
    printerManager = null;
  }
}