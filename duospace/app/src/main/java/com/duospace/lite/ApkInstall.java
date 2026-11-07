package com.duospace.lite;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.OpenableColumns;
import java.io.InputStream;
import java.io.OutputStream;

/** Stream package splits without blocking the UI or loading APKs into RAM. */
final class ApkInstall {
    interface Progress { void update(long copied, long total); }
    private ApkInstall() {}

    static void install(Activity activity, String appId, ClipData clip, Progress progress) throws Exception {
        if (clip == null || clip.getItemCount() == 0 || clip.getItemCount() > 48) {
            throw new IllegalArgumentException("No se recibieron los APK de la aplicación.");
        }
        long total = 0L;
        for (int i = 0; i < clip.getItemCount(); i++) {
            Uri uri = checkedUri(clip.getItemAt(i).getUri());
            try (Cursor c = activity.getContentResolver().query(uri,
                    new String[]{OpenableColumns.SIZE}, null, null, null)) {
                if (c != null && c.moveToFirst() && !c.isNull(0)) {
                    long size = c.getLong(0);
                    if (size > 0) total += size;
                }
            }
        }
        PackageInstaller installer = activity.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(appId);
        if (Build.VERSION.SDK_INT >= 31) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED);
        }
        int id = installer.createSession(params);
        boolean committed = false;
        try {
            try (PackageInstaller.Session session = installer.openSession(id)) {
                long copied = 0L;
                byte[] buffer = new byte[64 * 1024];
                for (int i = 0; i < clip.getItemCount(); i++) {
                    Uri uri = checkedUri(clip.getItemAt(i).getUri());
                    try (InputStream input = activity.getContentResolver().openInputStream(uri)) {
                        if (input == null) throw new java.io.IOException("No se puede leer el archivo " + (i + 1));
                        String name = i == 0 ? "base.apk" : "split_" + i + ".apk";
                        try (OutputStream output = session.openWrite(name, 0, -1)) {
                            int count;
                            while ((count = input.read(buffer)) != -1) {
                                output.write(buffer, 0, count);
                                copied += count;
                                progress.update(copied, total);
                            }
                            session.fsync(output);
                        }
                    }
                }
                Intent status = new Intent(activity, InstallProgressActivity.class);
                status.setAction("com.duospace.lite.INSTALL_RESULT." + id);
                status.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                status.putExtra(BridgeActivity.EXTRA_PACKAGE, appId);
                PendingIntent pending = PendingIntent.getActivity(activity, id, status,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
                session.commit(pending.getIntentSender());
                committed = true;
            }
        } finally {
            if (!committed) installer.abandonSession(id);
        }
    }

    private static Uri checkedUri(Uri uri) {
        if (uri == null || !"content".equals(uri.getScheme()) ||
                !SourceApkProvider.AUTHORITY.equals(uri.getAuthority())) {
            // Prefixes used by Android when addressing another user can vary.
            String authority = uri == null ? null : uri.getAuthority();
            if (uri == null || !"content".equals(uri.getScheme()) || authority == null ||
                    !authority.endsWith("@" + SourceApkProvider.AUTHORITY)) {
                throw new SecurityException("Origen del APK no autorizado.");
            }
        }
        return uri;
    }
}
