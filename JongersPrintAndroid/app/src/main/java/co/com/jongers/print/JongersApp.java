package co.com.jongers.print;

import android.app.Application;

/**
 * Registra el tipo de fallo de Android en las preferencias privadas del equipo.
 * Nunca registra codigos temporales, tickets ni credenciales.
 */
public final class JongersApp extends Application {
    @Override public void onCreate() {
        super.onCreate();
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                String trace = android.util.Log.getStackTraceString(error);
                if (trace.length() > 1350) trace = trace.substring(0, 1350);
                SecureStore.publicPrefs(this).edit()
                    .putString("last_print_crash", trace)
                    .putLong("last_print_diagnostic_at", System.currentTimeMillis())
                    .commit();
            } catch (Throwable ignored) { }
            if (previous != null) previous.uncaughtException(thread, error);
        });
    }
}
