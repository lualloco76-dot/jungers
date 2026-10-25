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
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import android.util.Log;
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

    private void note(String state) {
        Log.i("JongersPrintLibre", state);
        try {
            SecureStore.publicPrefs(this).edit()
                .putString("last_print_diagnostic", state.length() > 600 ? state.substring(0,600) : state)
                .putLong("last_print_diagnostic_at", System.currentTimeMillis()).apply();
        } catch (Throwable ignored) { }
    }
    private static String explain(Throwable e) {
        String message = e.getMessage();
        return e.getClass().getSimpleName()+": "+(message == null ? "Sin detalle" : message);
    }

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
            try {
                for (String id : PrinterConnections.discover(JongersAndroidPrintService.this)) {
                    try { printers.add(describePrinter(id)); }
                    catch (Throwable badPrinter) { note("Configuracion incompatible: " + explain(badPrinter)); }
                }
                if (!printers.isEmpty()) addPrinters(printers);
                else note("No hay impresoras configuradas. Abre Jonger's Print Libre para seleccionar una.");
            } catch (Throwable failure) { note("Descubrimiento: " + explain(failure)); }
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
        try {
            if (job == null || !job.isQueued() || !job.start()) return;
            String jobId = job.getId().toString();
            AtomicBoolean cancel = new AtomicBoolean(false);
            cancelled.put(jobId, cancel);
            // PrintJob y PrintDocument exigen el hilo principal de Android.
            // Capturamos todos los datos necesarios ANTES de pasar el trabajo al ejecutor.
            if (job.getInfo() == null || job.getInfo().getPrinterId() == null)
                throw new IOException("Android no indico la impresora de destino");
            final String printer = job.getInfo().getPrinterId().getLocalId();
            if (job.getDocument() == null)
                throw new IOException("Android no entrego el documento");
            final ParcelFileDescriptor data = job.getDocument().getData();
            if (data == null)
                throw new IOException("No fue posible acceder al PDF enviado por Android");
            note("Trabajo recibido de Android: " + jobId + ". Leyendo documento...");
            try {
                queue.execute(() -> {
                    String error = null;
                    try {
                        if (cancel.get()) throw new IOException("Impresion cancelada");
                        if (!PrinterConnections.discover(this).contains(printer))
                            throw new IOException("Impresora no disponible: " + printer
                                + ". Abre Jonger's Print Libre y realiza una prueba.");
                        int dots = SecureStore.publicPrefs(this).getInt("paper_width_dots", 384);
                        if (dots != 384 && dots != 576) dots = 384;
                        byte[] escpos = pdfToEscPos(data, dots, cancel);
                    if (cancel.get()) throw new IOException("Impresion cancelada");
                    PrinterConnections.print(this, printer, escpos);
                    long count = SecureStore.publicPrefs(this).getLong("system_print_count", 0L);
                    SecureStore.publicPrefs(this).edit().putLong("system_print_count", count + 1).apply();
                    note("Ticket transmitido a " + printer + ". Confirma la salida del papel.");
                    } catch (Throwable e) {
                        error = explain(e);
                        note("Error al imprimir: " + error);
                    } finally {
                        try { data.close(); } catch (IOException ignored) { }
                    }
                    final String result = error;
                try {
                    main.post(() -> {
                        cancelled.remove(jobId);
                        try {
                            if (cancel.get()) {
                                if (!job.isCancelled()) job.cancel();
                            } else if (result == null) {
                                job.complete();
                            } else {
                                job.fail(result);
                            }
                        } catch (Throwable finishError) {
                            note("Error de finalizacion: " + explain(finishError));
                        }
                    });
                    } catch (Throwable completionError) {
                        note("Error al responder a Android: " + explain(completionError));
                    }
                });
            } catch (RuntimeException queueError) {
                try { data.close(); } catch (IOException ignored) { }
                throw queueError;
            }
        } catch (Throwable startupError) {
            note("Error al aceptar documento: " + explain(startupError));
            try { if (job != null) job.fail(explain(startupError)); } catch (Throwable ignored) { }
        }
    }

    @Override
    protected void onRequestCancelPrintJob(PrintJob job) {
        try {
            AtomicBoolean state = cancelled.get(job.getId().toString());
            if (state != null) state.set(true);
            job.cancel();
        } catch (Throwable failure) { note("Error al cancelar: " + explain(failure)); }
    }

    /**
     * Renderiza PDF con memoria acotada, a lo ancho real de los cabezales ESC/POS.
     * Rollo 58 mm: 384 puntos. Rollo 80 mm: 576 puntos.
     */
    private byte[] pdfToEscPos(ParcelFileDescriptor fd, int dots, AtomicBoolean cancel)
        throws Exception {
        if (fd == null) throw new IOException("El documento no tiene contenido PDF");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[]{0x1b, 0x40, 0x1b, 0x61, 0x00});
        // Android puede proporcionar un descriptor secuencial; PdfRenderer requiere
        // uno que permita posicionarse. Copiamos el PDF a un archivo temporal privado.
        File temp = File.createTempFile("jongers_receipt_", ".pdf", getCacheDir());
        try {
            try (InputStream input = new ParcelFileDescriptor.AutoCloseInputStream(fd);
                 OutputStream target = new FileOutputStream(temp)) {
                byte[] block = new byte[8192];
                long total = 0L;
                int read;
                while ((read = input.read(block)) != -1) {
                    if (cancel.get()) throw new IOException("Cancelado");
                    total += read;
                    if (total > 8L * 1024L * 1024L)
                        throw new IOException("PDF demasiado grande (maximo 8 MB)");
                    target.write(block, 0, read);
                }
                if (total < 8L) throw new IOException("Android entrego un PDF vacio");
            }
            try (ParcelFileDescriptor localFile = ParcelFileDescriptor.open(
                    temp, ParcelFileDescriptor.MODE_READ_ONLY);
                 PdfRenderer pdf = new PdfRenderer(localFile)) {
            if (pdf.getPageCount() == 0) throw new IOException("Documento PDF vacío");
            if (pdf.getPageCount() > 40) throw new IOException("Documento excesivamente largo");
            int printedPages = 0;
            int removedBlankDots = 0;
            for (int p = 0; p < pdf.getPageCount(); p++) {
                if (cancel.get()) throw new IOException("Cancelado");
                try (PdfRenderer.Page page = pdf.openPage(p)) {
                    if (page.getWidth() <= 0 || page.getHeight() <= 0)
                        throw new IOException("Dimensiones PDF incorrectas");
                    float scale = dots / (float) page.getWidth();
                    int fullHeight = (int) Math.ceil(page.getHeight() * scale);
                    if (fullHeight <= 0 || fullHeight > 16000)
                        throw new IOException("Página demasiado larga");
                    int bytesPerRow = (dots + 7) / 8;
                    ByteArrayOutputStream pixels = new ByteArrayOutputStream(
                        Math.min(256 * 1024, fullHeight * bytesPerRow));
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
                            rasterRows(bitmap, pixels);
                        } finally {
                            bitmap.recycle();
                        }
                    }
                    byte[] raw = pixels.toByteArray();
                    int lastContentRow = -1;
                    for (int row = fullHeight - 1; row >= 0 && lastContentRow < 0; row--) {
                        int offset = row * bytesPerRow;
                        for (int j = 0; j < bytesPerRow; j++) {
                            if (raw[offset + j] != 0) {
                                lastContentRow = row;
                                break;
                            }
                        }
                    }
                    if (lastContentRow < 0) {
                        removedBlankDots += fullHeight;
                        continue; // Páginas sin contenido nunca deben gastar papel.
                    }
                    int keptRows = lastContentRow + 1;
                    removedBlankDots += fullHeight - keptRows;
                    writeRasterRows(raw, bytesPerRow, keptRows, out);
                    printedPages++;
                    if (out.size() > 2 * 1024 * 1024)
                        throw new IOException("Ticket mayor al tamaño permitido");
                    // Las páginas consecutivas comparten un único rollo de papel.
                    // No emitir salto de página ni alimentaciones grandes.
                    if (p + 1 < pdf.getPageCount())
                        out.write(new byte[]{0x1b, 0x4a, 0x08});
                }
            }
            if (printedPages == 0)
                throw new IOException("El PDF no contiene texto ni imágenes imprimibles");
            int feedDots = SecureStore.publicPrefs(this).getInt("bottom_feed_dots", 16);
            if (feedDots < 0 || feedDots > 80) feedDots = 16;
            // ESC J alimenta SOLO los puntos elegidos: por defecto ~2 mm a 203 dpi.
            if (feedDots > 0) out.write(new byte[]{0x1b, 0x4a, (byte)feedDots});
            note("Ticket optimizado: " + printedPages + " pagina(s); "
                + Math.round(removedBlankDots * 25.4f / 203f)
                + " mm de espacio blanco recortado. Margen final: "
                + Math.round(feedDots * 25.4f / 203f) + " mm.");
        }
        } finally {
            if (!temp.delete()) temp.deleteOnExit();
        }
        out.write(new byte[]{0x1d, 0x56, 0x00});
        return out.toByteArray();
    }

    /** Produce los bits de cada línea. Blanco = 0, negro = 1. */
    private static void rasterRows(Bitmap bitmap, ByteArrayOutputStream rows) throws IOException {
        int width = bitmap.getWidth(), height = bitmap.getHeight();
        int rowBytes = (width + 7) / 8;
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
            rows.write(row, 0, row.length);
        }
    }

    /** Emite raster ESC/POS GS v 0, únicamente hasta la última línea con tinta. */
    private static void writeRasterRows(byte[] raw, int rowBytes, int totalRows,
                                        ByteArrayOutputStream out) throws IOException {
        for (int start = 0; start < totalRows; start += 192) {
            int count = Math.min(192, totalRows - start);
            out.write(new byte[]{0x1d, 0x76, 0x30, 0x00,
                (byte)(rowBytes & 255), (byte)((rowBytes >> 8) & 255),
                (byte)(count & 255), (byte)((count >> 8) & 255)});
            out.write(raw, start * rowBytes, count * rowBytes);
        }
    }

    @Override public void onDestroy() {
        queue.shutdownNow();
        super.onDestroy();
    }
}
