package com.duospace.lite;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

/** A visible installation flow; never streams a large APK on the main thread. */
public final class InstallProgressActivity extends Activity {
    private TextView title, description, percent;
    private ProgressBar progress;
    private Button continueButton, storeButton, doneButton;
    private String target, label;
    private boolean started;
    private Intent confirmation;
    private static final int CONFIRM_REQUEST = 911;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        target = getIntent().getStringExtra(BridgeActivity.EXTRA_PACKAGE);
        label = getIntent().getStringExtra(BridgeActivity.EXTRA_LABEL);
        if (label == null || label.isEmpty()) label = target == null ? "Aplicación" : target;
        render();
        if (getIntent().hasExtra(PackageInstaller.EXTRA_STATUS)) {
            onInstallStatus(getIntent());
        } else if (target != null && getIntent().getClipData() != null) {
            startInstall(getIntent().getClipData());
        } else {
            fail("No se encontró una solicitud de instalación válida.");
        }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent.hasExtra(PackageInstaller.EXTRA_STATUS)) onInstallStatus(intent);
    }

    private void render() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Design.BACK);
        LinearLayout layout = Design.column(this, 22);
        scroll.addView(layout);
        setContentView(scroll);
        layout.addView(Design.copy(this, "DUOSPACE · INSTALACIÓN", 13), Design.lp(this, -1, -2, 10));
        title = Design.title(this, "Instalando " + label, 24);
        layout.addView(title, Design.lp(this, -1, -2, 26));
        description = Design.copy(this, "Preparando los archivos para el perfil independiente…", 15);
        layout.addView(description, Design.lp(this, -1, -2, 16));
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        layout.addView(progress, Design.lp(this, -1, 9, 24));
        percent = Design.copy(this, "0 %", 13);
        layout.addView(percent, Design.lp(this, -1, -2, 10));
        continueButton = Design.button(this, "Continuar instalación");
        continueButton.setVisibility(View.GONE);
        layout.addView(continueButton, Design.lp(this, -1, 52, 26));
        storeButton = Design.button(this, "Alternativa: Play Store del perfil");
        storeButton.setBackground(Design.shape(0xFF354367, Design.dp(this, 14)));
        layout.addView(storeButton, Design.lp(this, -1, 52, 18));
        storeButton.setOnClickListener(v -> openStore());
        doneButton = Design.button(this, "Comprobar y abrir DuoSpace");
        layout.addView(doneButton, Design.lp(this, -1, 52, 12));
        doneButton.setOnClickListener(v -> {
            try {
                if (target != null && getPackageManager().getLaunchIntentForPackage(target) != null) {
                    title.setText("Copia detectada");
                    description.setText("La aplicación ya está disponible en este perfil.");
                } else {
                    description.setText("Si Android aún no terminó, autoriza la instalación. " +
                            "Si no aparece ningún diálogo, prueba instalarla desde Play Store del perfil.");
                }
            } catch (RuntimeException e) {
                description.setText("No se pudo comprobar la copia: " + e.getMessage());
            }
        });
    }

    private void startInstall(ClipData clip) {
        if (started) return;
        started = true;
        title.setText("Copiando " + label);
        description.setText("Transferencia al perfil independiente. Puedes ver el avance; " +
                "no cierres esta pantalla hasta que Android pida autorización.");
        new Thread(() -> {
            try {
                ApkInstall.install(this, target, clip, (copied, total) -> runOnUiThread(() -> {
                    if (total > 0) {
                        int p = (int) Math.min(100L, (100L * copied) / total);
                        progress.setProgress(p);
                        percent.setText(p + " % · " + (copied / 1048576) + " MB transferidos");
                    } else {
                        percent.setText((copied / 1048576) + " MB transferidos");
                    }
                }));
                runOnUiThread(() -> {
                    title.setText("Esperando confirmación");
                    description.setText("Los archivos se copiaron. Android debe mostrar la autorización " +
                            "para finalizar la instalación. Si se solicita, pulsa Continuar instalación.");
                });
            } catch (Exception e) {
                runOnUiThread(() -> fail("No se pudo preparar el APK: " + e.getClass().getSimpleName() +
                        ": " + e.getMessage()));
            }
        }, "duospace-apk-transfer").start();
    }

    private void onInstallStatus(Intent intent) {
        int result = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (result == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            if (Build.VERSION.SDK_INT >= 33) {
                confirmation = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class);
            } else {
                confirmation = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            }
            title.setText("Falta tu autorización");
            description.setText("Android necesita que autorices la instalación de " + label + ".");
            if (confirmation != null) {
                continueButton.setVisibility(View.VISIBLE);
                continueButton.setOnClickListener(v -> {
                    continueButton.setVisibility(View.GONE);
                    try { startActivityForResult(confirmation, CONFIRM_REQUEST); }
                    catch (RuntimeException e) { fail("Android no abrió la autorización: " + e.getMessage()); }
                });
            } else {
                fail("Android no proporcionó la pantalla de autorización.");
            }
        } else if (result == PackageInstaller.STATUS_SUCCESS) {
            progress.setProgress(100);
            percent.setText("100 % · Instalación completada");
            title.setText("Copia instalada");
            description.setText("La aplicación quedó instalada en este perfil con datos independientes.");
            continueButton.setVisibility(View.GONE);
        } else {
            String detail = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
            fail("Android rechazó la instalación (código " + result + ")" +
                    (detail == null ? "." : ": " + detail));
        }
    }

    @Override protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (code == CONFIRM_REQUEST) {
            if (result == RESULT_CANCELED) {
                description.setText("La autorización no se completó. Puedes intentar con Play Store del perfil.");
            } else {
                title.setText("Finalizando instalación");
                description.setText("Android está comprobando e instalando la aplicación. " +
                        "Pulsa Comprobar si no ves el resultado.");
            }
        }
    }

    private void fail(String explanation) {
        title.setText("Instalación detenida");
        description.setText(explanation);
        continueButton.setVisibility(View.GONE);
    }

    private void openStore() {
        if (target == null) return;
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + target));
        try { startActivity(i); }
        catch (RuntimeException e) {
            try { startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=" + target))); }
            catch (RuntimeException ignored) { fail("No hay tienda disponible en el perfil de trabajo."); }
        }
    }
}
