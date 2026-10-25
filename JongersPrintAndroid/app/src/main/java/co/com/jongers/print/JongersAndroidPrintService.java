package co.com.jongers.print;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.pdf.PdfRenderer;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.print.PrintAttributes;
import android.print.PrinterId;
import android.print.PrinterInfo;
import android.printservice.PrintJob;
import android.printservice.PrinterDiscoverySession;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Servicio de impresión Android sin suscripciones ni límites por ticket.
 * El sistema Android entrega un documento PDF; aquí se convierte a ESC/POS.
 * Es independiente del enlace remoto FuegoPOS y funciona incluso sin emparejar la API.
 */
public final class JongersAndroidPrintService extends android.printservice.PrintService {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService queue = Executors.newSingleThreadExecutor();
    private final ConcurrentHashMap<String, AtomicBoolean> cancelled = new ConcurrentHashMap<>();

    @Override
    protected PrinterDiscoverySession onCreatePrinterDiscoverySession() {
        return new Session();
    }

    private PrinterInfo describePrinter(String id) {
        int dots = SecureStore.publicPrefs(this).getInt("paper_width_dots", 384);
        boolean wide = dots == 576;
        String display = id;
        if (id.startsWith("USB:")) display = "Jonger's USB · " + id.substring(4);
        if (id.startsWith("BT:")) display = "Jonger's Bluetooth · " + id.substring(3);
        if (id.startsWith("TCP:")) display = "Jonger's Wi-Fi · " + id.substring(4);
        PrinterId printerId = generatePrinterId(id);
        PrintAttributes.MediaSize paper = new PrintAttributes.MediaSize(
            wide ? "JONGERS_ROLL80" : "JONGERS_ROLL58",
            wide ? "Rollo 80 mm" : "Rollo 58 mm",
            wide ? 3150 : 2283, 11811
        );
        android.print.PrinterCapabilitiesInfo caps = new android.print.PrinterCapabilitiesInfo.Builder(printerId)
            .addMediaSize(paper, true)
            .addResolution(new PrintAttributes.Resolution("203dpi", "203 DPI", 203, 203), true)
            .setColorModes(PrintAttributes.COLOR_MODE_MONOCHROME, PrintAttributes.COLOR_MODE_MONOCHROME)
            .setMinMargins(new PrintAttributes.Margins(0, 0, 0, 0))
            .build();
        return new PrinterInfo.Builder(printerId, display, PrinterInfo.STATUS_IDLE)
            .setDescription("ESC/POS · sin suscripción · " + (wide ? "80" : "58") + " mm")
            .setCapabilities(caps)
            .build();
    }

    private final class Session extends PrinterDiscoverySession {
        private void reload() {
            if (isDestroyed()) return;
            List<PrinterInfo> printers = new ArrayList<>();
            for (String id : PrinterConnections.discover(JongersAndroidPrintService.this)) {
                printers.add(describePrinter(id));
            }
            if (!printers.isEmpty()) addPrinters(printers);
        }
        @Override public void onStartPrinterDiscovery(List<PrinterId> priorityList) { reload(); }
        @Override public void onStopPrinterDiscovery() { }
        @Override public void onValidatePrinters(List<PrinterId> printerIds) { reload(); }
        @Override public void onStartPrinterStateTracking(PrinterId printerId) { reload(); }
        @Override public void onStopPrinterStateTracking(PrinterId printerId) { }
        @Override public void onDestroy() { }
    }

    @Override
    protected void onPrintJobQueued(PrintJob job) {
        if (!job.isQueued() || !job.start()) return;
        String jobId = job.getId().toString();
        AtomicBoolean cancel = new AtomicBoolean(false);
        cancelled.put(jobId, cancel);
        queue.execute(() -> {
            String error = null;
            try {
                if (cancel.get()) throw new IOException("Impresión cancelada");
                String printer = job.getInfo().getPrinterId().getLocalId();
                if (!PrinterConnections.discover(this).contains(printer))
                    throw new IOException("Impresora no disponible. Configúrala en Jonger's Print Libre.");
                int dots = SecureStore.publicPrefs(this).getInt("paper_width_dots", 384);
                if (dots != 384 && dots != 576) dots = 384;
                byte[] escpos = pdfToEscPos(job.getDocument().getData(), dots, cancel);
                if (cancel.get()) throw new IOException("Impresión cancelada");
                PrinterConnections.print(this, printer, escpos);
                long count = SecureStore.publicPrefs(this).getLong("system_print_count", 0L);
                SecureStore.publicPrefs(this).edit().putLong("system_print_count", count + 1).apply();
            } catch (Exception e) {
                error = e.getMessage() == null ? "Error de impresora" : e.getMessage();
            }
            final String result = error;
            main.post(() -> {
                cancelled.remove(jobId);
                if (cancel.get()) {
                    if (!job.isCancelled()) job.cancel();
                } else if (result == null) {
                    job.complete();
                } else {
                    job.fail(result);
                }
            });
        });
    }

    @Override
    protected void onRequestCancelPrintJob(PrintJob job) {
        AtomicBoolean state = cancelled.get(job.getId().toString());
        if (state != null) state.set(true);
        job.cancel();
    }

    /**
     * Renderiza PDF con memoria acotada, a lo ancho real de los cabezales ESC/POS.
     * Rollo 58 mm: 384 puntos. Rollo 80 mm: 576 puntos.
     */
    private static byte[] pdfToEscPos(ParcelFileDescriptor fd, int dots, AtomicBoolean cancel)
        throws Exception {
        if (fd == null) throw new IOException("El documento no tiene contenido PDF");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[]{0x1b, 0x40, 0x1b, 0x61, 0x00});
        try (PdfRenderer pdf = new PdfRenderer(fd)) {
            if (pdf.getPageCount() == 0) throw new IOException("Documento PDF vacío");
            if (pdf.getPageCount() > 40) throw new IOException("Documento excesivamente largo");
            for (int p = 0; p < pdf.getPageCount(); p++) {
                if (cancel.get()) throw new IOException("Cancelado");
                try (PdfRenderer.Page page = pdf.openPage(p)) {
                    float scale = dots / (float) page.getWidth();
                    int fullHeight = (int) Math.ceil(page.getHeight() * scale);
                    if (fullHeight <= 0 || fullHeight > 16000)
                        throw new IOException("Página demasiado larga");
                    for (int top = 0; top < fullHeight; top += 192) {
                        if (cancel.get()) throw new IOException("Cancelado");
                        int height = Math.min(192, fullHeight - top);
                        Bitmap bitmap = Bitmap.createBitmap(dots, height, Bitmap.Config.ARGB_8888);
                        try {
                            bitmap.eraseColor(0xffffffff);
                            Matrix matrix = new Matrix();
                            matrix.setScale(scale, scale);
                            matrix.postTranslate(0, -top);
                            page.render(bitmap, new Rect(0, 0, dots, height), matrix,
                                PdfRenderer.Page.RENDER_MODE_FOR_PRINT);
                            raster(bitmap, out);
                        } finally {
                            bitmap.recycle();
                        }
                        if (out.size() > 2 * 1024 * 1024)
                            throw new IOException("Ticket mayor al tamaño permitido");
                    }
                }
                out.write(new byte[]{0x1b, 0x64, 0x02});
            }
        }
        out.write(new byte[]{0x1d, 0x56, 0x00});
        return out.toByteArray();
    }

    private static void raster(Bitmap bitmap, ByteArrayOutputStream out) throws IOException {
        int width = bitmap.getWidth(), height = bitmap.getHeight();
        int rowBytes = (width + 7) / 8;
        out.write(new byte[]{0x1d, 0x76, 0x30, 0x00,
            (byte)(rowBytes & 255), (byte)((rowBytes >> 8) & 255),
            (byte)(height & 255), (byte)((height >> 8) & 255)});
        int[] pixels = new int[width];
        byte[] row = new byte[rowBytes];
        for (int y = 0; y < height; y++) {
            bitmap.getPixels(pixels, 0, width, 0, y, width, 1);
            java.util.Arrays.fill(row, (byte)0);
            for (int x = 0; x < width; x++) {
                int c = pixels[x];
                int alpha = c >>> 24;
                int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
                int lum = (r * 299 + g * 587 + b * 114) / 1000;
                if (alpha >= 128 && lum < 178)
                    row[x >> 3] |= (byte)(0x80 >> (x & 7));
            }
            out.write(row, 0, row.length);
        }
    }

    @Override public void onDestroy() {
        queue.shutdownNow();
        super.onDestroy();
    }
}
