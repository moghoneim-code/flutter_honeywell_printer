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
import com.rt.printerlibrary.enumerate.ConnectStateEnum;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class HoneywellPrinterManager implements PrinterObserver {
    private static final String TAG = "HoneywellPrinterManager";
    private RTPrinter rtPrinter;
    private Context context;
    private boolean isConnected = false;
    private final int CONNECTION_TIMEOUT = 10; // seconds

    // Keep track of the last connection result for better debugging
    private String lastConnectionError = null;

    public HoneywellPrinterManager(Context context) {
        this.context = context;
        PrinterObserverManager.getInstance().add(this);
    }

    /**
     * Connect to a printer using its MAC address
     */
    public void connectPrinter(String macAddress, final PrinterCallback callback) {
        try {
            Log.d(TAG, "Connecting to printer with MAC address: " + macAddress);

            // Reset connection status and error message
            isConnected = false;
            lastConnectionError = null;

            // Check if printer is already connected
            if (rtPrinter != null) {
                try {
                    rtPrinter.disConnect();
                } catch (Exception e) {
                    Log.w(TAG, "Error disconnecting previous printer connection", e);
                }
            }

            BluetoothDevice device = getDeviceByAddress(macAddress);
            if (device == null) {
                String errorMsg = "Device not found or not paired. Please check Bluetooth settings.";
                Log.e(TAG, errorMsg);
                callback.onResult(false, errorMsg);
                return;
            }

            Log.d(TAG, "Found Bluetooth device: " + device.getName());

            // Create printer instance
            PrinterFactory printerFactory = new UniversalPrinterFactory();
            rtPrinter = printerFactory.create();

            // Set up connection
            BluetoothFactory bluetoothFactory = new BluetoothFactory();
            PrinterInterface printerInterface = bluetoothFactory.create();
            BluetoothEdrConfigBean configBean = new BluetoothEdrConfigBean(device);
            printerInterface.setConfigObject(configBean);
            rtPrinter.setPrinterInterface(printerInterface);

            // Use a latch to wait for connection result
            final CountDownLatch latch = new CountDownLatch(1);

            // Connect to the printer
            Log.d(TAG, "Initiating connection to printer...");
            rtPrinter.connect(configBean);

            // Wait for connection result in a separate thread
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        // Wait for connection callback or timeout
                        boolean completed = latch.await(CONNECTION_TIMEOUT, TimeUnit.SECONDS);

                        if (!completed) {
                            lastConnectionError = "Connection timed out after " + CONNECTION_TIMEOUT + " seconds";
                            Log.e(TAG, lastConnectionError);
                        }

                        if (isConnected) {
                            Log.d(TAG, "Successfully connected to printer");
                            callback.onResult(true, "Connected to " + device.getName());
                        } else {
                            String errorMsg = lastConnectionError != null ? lastConnectionError : "Failed to connect";
                            Log.e(TAG, errorMsg);
                            callback.onResult(false, errorMsg);

                            // Cleanup if connection failed
                            if (rtPrinter != null) {
                                try {
                                    rtPrinter.disConnect();
                                } catch (Exception e) {
                                    Log.w(TAG, "Error cleaning up after failed connection", e);
                                }
                            }
                        }
                    } catch (InterruptedException e) {
                        Log.e(TAG, "Connection thread interrupted", e);
                        callback.onResult(false, "Connection interrupted: " + e.getMessage());
                    }
                }
            }).start();

            // Release the latch after a short delay to allow connection callback to potentially be called
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(5000); // Wait 5 seconds before forcing result
                        latch.countDown();
                    } catch (InterruptedException e) {
                        Log.e(TAG, "Latch release thread interrupted", e);
                    }
                }
            }).start();

        } catch (Exception e) {
            Log.e(TAG, "Error connecting to printer", e);
            callback.onResult(false, "Error connecting: " + e.getMessage());
        }
    }

    /**
     * Get a Bluetooth device by its MAC address
     */
    private BluetoothDevice getDeviceByAddress(String address) {
        try {
            BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
            if (bluetoothManager == null) {
                Log.e(TAG, "BluetoothManager not available");
                return null;
            }

            BluetoothAdapter adapter = bluetoothManager.getAdapter();
            if (adapter == null) {
                Log.e(TAG, "BluetoothAdapter not available");
                return null;
            }

            if (!adapter.isEnabled()) {
                Log.e(TAG, "Bluetooth is disabled");
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

            Log.e(TAG, "No paired device with address: " + address);
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Error getting Bluetooth device", e);
            return null;
        }
    }

    /**
     * Print a PDF document
     */
    /**
     * Get all pages from a PDF file as bitmaps with proper scaling
     * @param pdfPath Path to the PDF file
     * @param printerWidthDots Width of the printer in dots (e.g., 384 dots for 48mm printer at 203 DPI)
     * @return List of bitmaps representing PDF pages
     */
    /**
     * Get all pages from a PDF file as bitmaps with proper scaling
     * @param pdfPath Path to the PDF file
     * @param printerWidthDots Width of the printer in dots
     * @return List of bitmaps representing PDF pages
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
            // Open the PDF file
            parcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
            renderer = new PdfRenderer(parcelFileDescriptor);

            Log.d(TAG, "PDF opened successfully, contains " + renderer.getPageCount() + " pages");

            // Render all pages
            final int pageCount = renderer.getPageCount();
            for (int i = 0; i < pageCount; i++) {
                Log.d(TAG, "Rendering page " + (i+1) + " of " + pageCount);

                PdfRenderer.Page page = renderer.openPage(i);

                // Get the original PDF page dimensions
                float pdfWidth = page.getWidth();
                float pdfHeight = page.getHeight();

                // Calculate scale to fit printer width
                float scale = printHeadWidth / pdfWidth;
                int scaledWidth = (int)(pdfWidth * scale);
                int scaledHeight = (int)(pdfHeight * scale);

                Log.d(TAG, "Page dimensions: " + pdfWidth + "x" + pdfHeight + ", scaled: " + scaledWidth + "x" + scaledHeight);

                // Create bitmap with white background
                Bitmap pageBitmap = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(pageBitmap);
                canvas.drawColor(Color.WHITE);

                // Render page to bitmap
                page.render(pageBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                pageImages.add(pageBitmap);
                page.close();
            }

            Log.d(TAG, "Successfully rendered all " + pageCount + " pages");

        } catch (IOException e) {
            Log.e(TAG, "IO error processing PDF", e);
        } catch (Exception e) {
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
                Log.e(TAG, "Error closing PDF resources", e);
            }
        }

        return pageImages;
    }
    /**
     * Print a PDF document as normal document rather than receipt
     */
    public void printPdf(String pdfPath, boolean withGap, final PrinterCallback callback) {
        if (!isConnected || rtPrinter == null) {
            Log.e(TAG, "Printer not connected");
            callback.onResult(false, "Printer not connected");
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File file = new File(pdfPath);
                    if (!file.exists()) {
                        Log.e(TAG, "PDF file does not exist: " + pdfPath);
                        callback.onResult(false, "PDF file not found: " + pdfPath);
                        return;
                    }

                    Log.d(TAG, "Starting PDF print job for file: " + pdfPath + ", size: " + file.length() + " bytes");

                    // Get PDF pages as bitmaps
                    List<Bitmap> pageImages = getAllPagesFromPDF(pdfPath, 576); // 72mm width at 203 DPI (8 dots per mm)

                    if (pageImages.isEmpty()) {
                        Log.e(TAG, "Failed to render PDF pages");
                        callback.onResult(false, "Failed to render PDF");
                        return;
                    }

                    Log.d(TAG, "Successfully rendered " + pageImages.size() + " PDF pages");

                    // Print each page
                    for (int i = 0; i < pageImages.size(); i++) {
                        Log.d(TAG, "Printing page " + (i+1) + " of " + pageImages.size());
                        Bitmap pageBitmap = pageImages.get(i);

                        // For all pages except the last one, trim bottom white space if no gap desired
                        if (!withGap && i < pageImages.size() - 1) {
                            pageBitmap = trimBottomWhitespace(pageBitmap);
                        }

                        printSingleBitmap(pageBitmap, i == 0, withGap);

                        // Release bitmap memory
                        if (!pageBitmap.isRecycled()) {
                            pageBitmap.recycle();
                        }

                        // Add a delay between print jobs to prevent printer buffer overflow
                        Thread.sleep(500);
                    }

                    Log.d(TAG, "PDF print job completed successfully");
                    callback.onResult(true, "PDF printed successfully");

                } catch (Exception e) {
                    Log.e(TAG, "Error printing PDF", e);
                    callback.onResult(false, "Error printing PDF: " + e.getMessage());
                }
            }
        }).start();
    }

/**
 * Get all pages from a PDF file as bitmaps
 */
    /**
     * Print a single bitmap as a document page
     * @return boolean indicating success
     */
    /**
     * Print a single bitmap as a document page
     * @return boolean indicating success
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
            for (int x = 0; x < width; x += 10) { // Sample every 10 pixels for performance
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
     * Print a single bitmap
     */
    private void printSingleBitmap(Bitmap mBitmap, boolean isFirstPage, boolean withGap) {
        try {
            if (mBitmap == null || mBitmap.isRecycled()) {
                Log.e(TAG, "Invalid bitmap for printing");
                return;
            }

            if (rtPrinter == null || !isConnected) {
                Log.e(TAG, "Printer not connected");
                return;
            }

            Log.d(TAG, "Preparing bitmap for printing, size: " + mBitmap.getWidth() + "x" + mBitmap.getHeight());

            // Create ZPL command
            CmdFactory zplFac = new ZplFactory();
            Cmd zplCmd = zplFac.create();

            // Add header command
            zplCmd.append(zplCmd.getHeaderCmd());

            // Configure common settings
            CommonSetting commonSetting = new CommonSetting();

            // Set label size based on bitmap dimensions
            // Height is calculated from bitmap height in dots to mm (8 dots per mm at 203 DPI)
            int labelWidthMm = 80; // Fixed at 80mm width
            int labelHeightMm = Math.max(mBitmap.getHeight() / 8, 10); // Ensure minimum 10mm height

            Log.d(TAG, "Setting label size to " + labelWidthMm + "x" + labelHeightMm + "mm");
            commonSetting.setLableSizeBean(new LableSizeBean(labelWidthMm, labelHeightMm));

            // Set minimum possible label gap only for the first page to establish printer settings
            if (isFirstPage) {
                int gapSize = withGap ? 3 : 0; // Use minimum gap if continuous printing is desired
                Log.d(TAG, "Setting label gap to " + gapSize + "mm");
                commonSetting.setLabelGap(gapSize);
            }

            // Set print direction (REVERSE prints from top to bottom)
            commonSetting.setPrintDirection(PrintDirection.REVERSE);

            // Apply common settings
            byte[] commonSettingCmd = zplCmd.getCommonSettingCmd(commonSetting);
            zplCmd.append(commonSettingCmd);

            // Set print position to start at the very top to minimize space
            byte[] pointXY = commonSetting.setPointXY(0, 0);
            rtPrinter.writeMsgAsync(pointXY);

            // Configure bitmap settings
            BitmapSetting bitmapSetting = new BitmapSetting();
            // Position the bitmap at the very top of the label
            bitmapSetting.setPrintPostion(new Position(0, 0));
            // Limit width to printer capacity (576 dots = 72mm at 8 dots/mm)
            bitmapSetting.setBimtapLimitWidth(576);

            // Generate bitmap command
            byte[] bitmapCmd = zplCmd.getBitmapCmd(bitmapSetting, mBitmap);
            zplCmd.append(bitmapCmd);

            // Print only one copy
            zplCmd.append(zplCmd.getPrintCopies(1));
            zplCmd.append(zplCmd.getEndCmd());

            // Send command to printer
            Log.d(TAG, "Sending print command to printer");
            rtPrinter.writeMsg(zplCmd.getAppendCmds());
        } catch (SdkException e) {
            Log.e(TAG, "SDK error printing bitmap", e);
        } catch (Exception e) {
            Log.e(TAG, "Error printing bitmap", e);
        }
    }
    /**
     * Alternative method to print bitmap if the standard method fails
     */
    private boolean printBitmapAlternative(Bitmap original, boolean isFirstPage, boolean withGap) {
        try {
            // Create a new bitmap with slightly reduced size to avoid scaling issues
            int newWidth = Math.min(original.getWidth(), 560); // Slightly less than 576
            int newHeight = (int)(original.getHeight() * ((float)newWidth / original.getWidth()));

            // Create a scaled copy with explicit dimensions
            Bitmap scaledBitmap = Bitmap.createScaledBitmap(original, newWidth, newHeight, true);

            // Configure printer
            CmdFactory cmdFactory = new ZplFactory();
            Cmd cmd = cmdFactory.create();

            // Header
            cmd.append(cmd.getHeaderCmd());

            // Common settings with simplified parameters
            CommonSetting setting = new CommonSetting();
            setting.setLableSizeBean(new LableSizeBean(70, 100)); // Fixed size label

            if (isFirstPage) {
                setting.setLabelGap(3); // Fixed gap
            }

            setting.setPrintDirection(PrintDirection.NORMAL);
            cmd.append(cmd.getCommonSettingCmd(setting));

            // Bitmap settings with minimal configuration
            BitmapSetting bmpSetting = new BitmapSetting();
            bmpSetting.setPrintPostion(new Position(0, 0));
            bmpSetting.setBimtapLimitWidth(560); // Fixed width limit

            // Add bitmap
            cmd.append(cmd.getBitmapCmd(bmpSetting, scaledBitmap));
            cmd.append(cmd.getPrintCopies(1));
            cmd.append(cmd.getEndCmd());

            // Print
            rtPrinter.writeMsg(cmd.getAppendCmds());

            // Clean up
            if (!scaledBitmap.equals(original)) {
                scaledBitmap.recycle();
            }

            return true;
        } catch (Exception e) {
            Log.e(TAG, "Alternative bitmap printing failed", e);
            return false;
        }
    }

    /**
     * Trim bottom whitespace from a bitmap
     */
//    private Bitmap trimBottomWhitespace(Bitmap bitmap) {
//        int width = bitmap.getWidth();
//        int height = bitmap.getHeight();
//
//        // Find the last non-white row
//        int lastNonWhiteRow = height - 1;
//        boolean foundNonWhite;
//
//        // Start from the bottom and scan upward
//        while (lastNonWhiteRow > 0) {
//            foundNonWhite = false;
//            for (int x = 0; x < width; x += 10) { // Sample every 10 pixels for performance
//                if (bitmap.getPixel(x, lastNonWhiteRow) != Color.WHITE) {
//                    foundNonWhite = true;
//                    break;
//                }
//            }
//
//            if (foundNonWhite) {
//                break;
//            }
//
//            lastNonWhiteRow--;
//        }
//
//        // Add a small margin to the bottom
//        lastNonWhiteRow = Math.min(lastNonWhiteRow + 5, height - 1);
//
//        // Crop the bitmap if we found white space to trim
//        if (lastNonWhiteRow < height - 1) {
//            Log.d(TAG, "Trimming bitmap from height " + height + " to " + (lastNonWhiteRow + 1));
//            return Bitmap.createBitmap(bitmap, 0, 0, width, lastNonWhiteRow + 1);
//        } else {
//            // No significant whitespace found
//            return bitmap;
//        }
//    }

    /**
     * Check if a printer is connected
     */
    public boolean isPrinterConnected() {
        return isConnected && rtPrinter != null;
    }

    /**
     * Get the last connection error message
     */
    public String getLastConnectionError() {
        return lastConnectionError;
    }

    /**
     * Disconnect from the printer
     */
    public void disconnectPrinter() {
        if (rtPrinter != null) {
            try {
                Log.d(TAG, "Disconnecting printer");
                rtPrinter.disConnect();
            } catch (Exception e) {
                Log.e(TAG, "Error disconnecting printer", e);
            } finally {
                isConnected = false;
            }
        }
    }


    @Override
    public void printerObserverCallback(PrinterInterface printerInterface, int state) {
        Log.d(TAG, "Printer observer callback state: " + state);

        switch (state) {
            case CommonEnum.CONNECT_STATE_SUCCESS:
                Log.d(TAG, "Printer connected successfully");
                isConnected = true;
                break;
//            case CommonEnum.CONNECT_STATE_FAILED:
//                lastConnectionError = "Connection failed";
//                Log.e(TAG, lastConnectionError);
//                isConnected = false;
//                break;
//            case CommonEnum.CONNECT_STATE_INTERRUPTED:
//                lastConnectionError = "Printer disconnected";
//                Log.d(TAG, "Printer disconnected");
//                isConnected = false;
//                break;
            case CommonEnum.CONNECT_STATE_INTERRUPTED:
                lastConnectionError = "Connection interrupted";
                Log.e(TAG, lastConnectionError);
                isConnected = false;
                break;
            default:
                // Handle any other states, including potential CONNECT_STATE_CONNECTING
                if (state == 4) { // Assuming 4 might be CONNECT_STATE_CONNECTING in some versions
                    Log.d(TAG, "Connecting to printer...");
                } else {
                    Log.d(TAG, "Unknown printer state: " + state);
                }
                break;
        }
    }

    @Override
    public void printerReadMsgCallback(PrinterInterface printerInterface, byte[] bytes) {
        if (bytes != null && bytes.length > 0) {
            Log.d(TAG, "Received message from printer: length=" + bytes.length);
        }
    }

    /**
     * Callback interface for printer operations
     */
    public interface PrinterCallback {
        void onResult(boolean success, String message);
    }
}