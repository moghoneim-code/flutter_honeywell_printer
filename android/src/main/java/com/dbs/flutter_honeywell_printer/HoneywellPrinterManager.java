package com.dbs.flutter_honeywell_printer;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.rt.printerlibrary.bean.BluetoothEdrConfigBean;
import com.rt.printerlibrary.bean.LableSizeBean;
import com.rt.printerlibrary.bean.Position;
import com.rt.printerlibrary.cmd.Cmd;
import com.rt.printerlibrary.cmd.ZplFactory;
import com.rt.printerlibrary.connect.PrinterInterface;
import com.rt.printerlibrary.enumerate.CommonEnum;
import com.rt.printerlibrary.enumerate.PrintDirection;
import com.rt.printerlibrary.exception.SdkException;
import com.rt.printerlibrary.factory.cmd.CmdFactory;
import com.rt.printerlibrary.factory.connect.BluetoothFactory;
import com.rt.printerlibrary.factory.printer.PrinterFactory;
import com.rt.printerlibrary.factory.printer.UniversalPrinterFactory;
import com.rt.printerlibrary.observer.PrinterObserver;
import com.rt.printerlibrary.observer.PrinterObserverManager;
import com.rt.printerlibrary.printer.RTPrinter;
import com.rt.printerlibrary.setting.BitmapSetting;
import com.rt.printerlibrary.setting.CommonSetting;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class HoneywellPrinterManager implements PrinterObserver {
    private static final String TAG = "HoneywellPrinterManager";
    private RTPrinter rtPrinter;
    private Context context;
    private boolean isConnected = false;

    public HoneywellPrinterManager(Context context) {
        this.context = context;
        PrinterObserverManager.getInstance().add(this);
    }

    /**
     * Connect to a printer using its MAC address
     */
    public void connectPrinter(String macAddress, final PrinterCallback callback) {
        try {
            ///  print to see
            Log.d(TAG, "Connecting to printer with MAC address from native: " + macAddress);
            BluetoothDevice device = getDeviceByAddress(macAddress);
            if (device == null) {
                callback.onResult(false, "Device not found");
                return;
            }

            // Create printer instance
            PrinterFactory printerFactory = new UniversalPrinterFactory();
            rtPrinter = printerFactory.create();

            // Connect to the printer
            BluetoothFactory bluetoothFactory = new BluetoothFactory();
            PrinterInterface printerInterface = bluetoothFactory.create();
            BluetoothEdrConfigBean configBean = new BluetoothEdrConfigBean(device);
            printerInterface.setConfigObject(configBean);
            rtPrinter.setPrinterInterface(printerInterface);
            rtPrinter.connect(configBean);

            // The actual connection result will be handled in printerObserverCallback
            // We'll wait a moment to see if the connection succeeds
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        // Wait for connection to establish
                        Thread.sleep(2000);
                        callback.onResult(isConnected, isConnected ? "Connected successfully" : "Connection timed out");
                    } catch (InterruptedException e) {
                        callback.onResult(false, "Connection interrupted");
                    }
                }
            }).start();

        } catch (Exception e) {
            Log.e(TAG, "Error connecting to printer from native", e);
            callback.onResult(false, "Error connecting: " + e.getMessage());
        }
    }

    /**
     * Get a Bluetooth device by its MAC address
     */
    private BluetoothDevice getDeviceByAddress(String address) {
        BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = bluetoothManager.getAdapter();

        if (adapter == null) {
            return null;
        }

        Set<BluetoothDevice> pairedDevices = adapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                if (device.getAddress().equals(address)) {
                    return device;
                }
            }
        }
        return null;
    }

    /**
     * Print a PDF document
     */
    public void printPdf(String pdfPath, boolean withGap, final PrinterCallback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // Get PDF pages as bitmaps
                    List<Bitmap> pageImages = getAllPagesFromPDF(pdfPath, 203 * 8); // 203 DPI is standard for most Honeywell printers

                    if (pageImages.isEmpty()) {
                        callback.onResult(false, "Failed to render PDF");
                        return;
                    }

                    // Print each page
                    for (int i = 0; i < pageImages.size(); i++) {
                        Bitmap pageBitmap = pageImages.get(i);

                        // For all pages except the last one, trim bottom white space if no gap desired
                        if (!withGap && i < pageImages.size() - 1) {
                            pageBitmap = trimBottomWhitespace(pageBitmap);
                        }

                        printSingleBitmap(pageBitmap, i == 0, withGap);

                        // Add a small delay between print jobs to prevent printer buffer overflow
                        Thread.sleep(100);
                    }

                    callback.onResult(true, "PDF printed successfully");
                } catch (Exception e) {
                    Log.e(TAG, "Error printing PDF", e);
                    callback.onResult(false, "Error printing PDF: " + e.getMessage());
                }
            }
        }).start();
    }

    /**
     * Print a single bitmap
     */
    private void printSingleBitmap(Bitmap mBitmap, boolean isFirstPage, boolean withGap) {
        try {
            CmdFactory zplFac = new ZplFactory();
            Cmd zplCmd = zplFac.create();
            zplCmd.append(zplCmd.getHeaderCmd());

            CommonSetting commonSetting = new CommonSetting();
            commonSetting.setLableSizeBean(new LableSizeBean(80, mBitmap.getHeight() / 8));

            // Set minimum possible label gap only for the first page to establish printer settings
            if (isFirstPage) {
                commonSetting.setLabelGap(withGap ? 3 : 0); // Use minimum gap if continuous printing is desired
            }

            // Set print position to start at the very top to minimize space
            byte[] pointXY = commonSetting.setPointXY(0, 0);
            if (rtPrinter != null) {
                rtPrinter.writeMsgAsync(pointXY);
            }

            commonSetting.setPrintDirection(PrintDirection.REVERSE);
            zplCmd.append(zplCmd.getCommonSettingCmd(commonSetting));

            BitmapSetting bitmapSetting = new BitmapSetting();
            // Position the bitmap at the very top of the label
            bitmapSetting.setPrintPostion(new Position(0, 0));
            bitmapSetting.setBimtapLimitWidth(203 * 8);

            zplCmd.append(zplCmd.getBitmapCmd(bitmapSetting, mBitmap));
            // Print only one copy
            zplCmd.append(zplCmd.getPrintCopies(1));
            zplCmd.append(zplCmd.getEndCmd());

            if (rtPrinter != null) {
                rtPrinter.writeMsg(zplCmd.getAppendCmds());
            }
        } catch (SdkException e) {
            Log.e(TAG, "Error printing bitmap", e);
        }
    }

    /**
     * Get all pages from a PDF file as bitmaps
     */
    private List<Bitmap> getAllPagesFromPDF(String pdfPath, int printHeadWidth) {
        List<Bitmap> pageImages = new ArrayList<>();
        ParcelFileDescriptor parcelFileDescriptor = null;
        PdfRenderer renderer = null;

        File file = new File(pdfPath);
        if (!file.exists()) {
            Log.e(TAG, "PDF file does not exist: " + pdfPath);
            return pageImages;
        }

        try {
            parcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
            renderer = new PdfRenderer(parcelFileDescriptor);

            // Render all pages
            final int pageCount = renderer.getPageCount();
            for (int i = 0; i < pageCount; i++) {
                PdfRenderer.Page page = renderer.openPage(i);
                float width = page.getWidth();
                float height = page.getHeight();
                float scale = printHeadWidth / width;
                float scaledWidth = width * scale;
                float scaledHeight = height * scale;

                Bitmap pageBitmap = Bitmap.createBitmap((int) scaledWidth, (int) scaledHeight, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(pageBitmap);
                canvas.drawColor(Color.WHITE); // Set background as WHITE

                // Render page to bitmap
                page.render(pageBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                pageImages.add(pageBitmap);
                page.close();
            }

            renderer.close();
            parcelFileDescriptor.close();
        } catch (IOException e) {
            Log.e(TAG, "Error processing PDF", e);
        } finally {
            try {
                if (renderer != null) {
                    renderer.close();
                }
                if (parcelFileDescriptor != null) {
                    parcelFileDescriptor.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "Error closing resources", e);
            }
        }

        return pageImages;
    }

    /**
     * Trim bottom whitespace from a bitmap
     */
    private Bitmap trimBottomWhitespace(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        // Find the last non-white row
        int lastNonWhiteRow = height - 1;
        boolean foundNonWhite;

        // Start from the bottom and scan upward
        while (lastNonWhiteRow > 0) {
            foundNonWhite = false;
            for (int x = 0; x < width; x++) {
                if (bitmap.getPixel(x, lastNonWhiteRow) != Color.WHITE) {
                    foundNonWhite = true;
                    break;
                }
            }

            if (foundNonWhite) {
                break;
            }

            lastNonWhiteRow--;
        }

        // Add a small margin to the bottom (just a few pixels)
        lastNonWhiteRow = Math.min(lastNonWhiteRow + 5, height - 1);

        // Crop the bitmap
        return Bitmap.createBitmap(bitmap, 0, 0, width, lastNonWhiteRow + 1);
    }

    /**
     * Check if a printer is connected
     */
    public boolean isPrinterConnected() {
        return isConnected;
    }

    /**
     * Disconnect from the printer
     */
    public void disconnectPrinter() {
        if (rtPrinter != null) {
            rtPrinter.disConnect();
            isConnected = false;
        }
    }

    @Override
    public void printerObserverCallback(PrinterInterface printerInterface, int state) {
        switch (state) {
            case CommonEnum.CONNECT_STATE_SUCCESS:
                isConnected = true;
                break;
            case CommonEnum.CONNECT_STATE_INTERRUPTED:
                isConnected = false;
                break;
        }
    }

    @Override
    public void printerReadMsgCallback(PrinterInterface printerInterface, byte[] bytes) {
        // Not needed for basic functionality
    }

    /**
     * Callback interface for printer operations
     */
    public interface PrinterCallback {
        void onResult(boolean success, String message);
    }
}
