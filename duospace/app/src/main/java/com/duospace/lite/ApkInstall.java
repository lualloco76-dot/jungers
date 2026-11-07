package com.duospace.lite;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import java.io.InputStream;
import java.io.OutputStream;

final class ApkInstall {
    private ApkInstall() {}

    static void install(Activity activity, String appId, ClipData clip) throws Exception {
        PackageInstaller installer=activity.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params=new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(appId);
        params.setInstallLocation(PackageInfoStub.LOCATION_AUTO);
        int id=installer.createSession(params);
        boolean committed=false;
        try {
            try(PackageInstaller.Session session=installer.openSession(id)) {
                byte[] buffer=new byte[65536];
                for(int i=0;i<clip.getItemCount();i++){
                    Uri uri=clip.getItemAt(i).getUri();
                    if(uri==null || !"content".equals(uri.getScheme()) ||
                            !SourceApkProvider.AUTHORITY.equals(uri.getAuthority())) {
                        // Android sometimes includes a user-id prefix in cross-profile URIs.
                        if(uri==null || !"content".equals(uri.getScheme()) ||
                                !uri.getAuthority().endsWith("@"+SourceApkProvider.AUTHORITY))
                            throw new SecurityException("Fuente no reconocida");
                    }
                    try(InputStream input=activity.getContentResolver().openInputStream(uri)){
                        if(input==null)throw new java.io.IOException("No se puede leer APK "+i);
                        String name=i==0?"base.apk":("split_"+i+".apk");
                        try(OutputStream output=session.openWrite(name,0,-1)) {
                            int read;
                            while((read=input.read(buffer))!=-1) output.write(buffer,0,read);
                            session.fsync(output);
                        }
                    }
                }
                Intent status=new Intent(activity,InstallStatusActivity.class);
                status.setAction("com.duospace.lite.STATUS_"+id);
                status.putExtra("target_package",appId);
                PendingIntent pending=PendingIntent.getActivity(activity,id,status,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
                session.commit(pending.getIntentSender());
                committed=true;
            }
        } finally {
            if(!committed) installer.abandonSession(id);
        }
    }

    // Standard Android default. Defined locally to avoid an extra dependency.
    static final class PackageInfoStub {static final int LOCATION_AUTO=0;}
}
