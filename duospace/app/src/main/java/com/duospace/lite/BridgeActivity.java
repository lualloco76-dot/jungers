package com.duospace.lite;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.content.ClipData;
import android.widget.Toast;

public final class BridgeActivity extends Activity {
    static final String ACTION_CLONE="com.duospace.lite.CLONE";
    static final String ACTION_OPEN_SPACE="com.duospace.lite.OPEN_SPACE";
    static final String EXTRA_PACKAGE="package";
    static final String EXTRA_LABEL="label";
    static final int REQUEST_SOURCE_PERMISSION=200;
    private String packageName;
    private String label;
    private ClipData sources;
    private boolean attempted;

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        setContentView(new android.widget.FrameLayout(this));
        DevicePolicyManager dpm=getSystemService(DevicePolicyManager.class);
        if(dpm==null || !dpm.isProfileOwnerApp(getPackageName())){
            finish();return;
        }
        if(ACTION_OPEN_SPACE.equals(getIntent().getAction())){
            startActivity(new Intent(this,MainActivity.class));finish();return;
        }
        if(!ACTION_CLONE.equals(getIntent().getAction())){finish();return;}
        packageName=getIntent().getStringExtra(EXTRA_PACKAGE);
        label=getIntent().getStringExtra(EXTRA_LABEL);
        sources=getIntent().getClipData();
        if(packageName==null || !packageName.matches("[a-zA-Z0-9_]+(\\.[a-zA-Z0-9_]+)+") ||
                sources==null || sources.getItemCount()<1 || sources.getItemCount()>48){
            error("Solicitud de instalación incompleta.");return;
        }
        if(label==null || label.isEmpty()) label=packageName;
        if(isInstalled()){
            new AlertDialog.Builder(this).setTitle("Ya está duplicada")
                    .setMessage(label+" ya está instalada en el perfil independiente.")
                    .setPositiveButton("Abrir",(d,w)->{
                        try{startActivity(getPackageManager().getLaunchIntentForPackage(packageName));}
                        catch(RuntimeException e){Toast.makeText(this,"No se pudo abrir.",Toast.LENGTH_LONG).show();}
                        finish();})
                    .setNegativeButton("Cerrar",(d,w)->finish()).setOnCancelListener(d->finish()).show();
            return;
        }
        new AlertDialog.Builder(this).setTitle("Crear copia independiente")
                .setMessage("Se instalará "+label+" en el perfil de trabajo. Sus datos y cuenta quedarán separados de la aplicación original. Android puede pedir autorización para instalar el APK.")
                .setPositiveButton("Duplicar",(d,w)->checkAndInstall())
                .setNeutralButton("Buscar en Play Store",(d,w)->openStore())
                .setNegativeButton("Cancelar",(d,w)->finish()).setOnCancelListener(d->finish()).show();
    }
    private boolean isInstalled(){
        try {getPackageManager().getPackageInfo(packageName,0);return true;}
        catch(PackageManager.NameNotFoundException e){return false;}
    }
    private void checkAndInstall(){
        if(!getPackageManager().canRequestPackageInstalls()){
            new AlertDialog.Builder(this).setTitle("Permiso de instalación")
                    .setMessage("Autoriza las instalaciones de DuoSpace dentro del perfil de trabajo. Si el teléfono no permite esta opción, utiliza la Play Store de ese perfil.")
                    .setPositiveButton("Configurar",(d,w)->{
                        Intent i=new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:"+getPackageName()));
                        try{startActivityForResult(i,REQUEST_SOURCE_PERMISSION);}catch(RuntimeException e){openStore();}
                    }).setNegativeButton("Play Store",(d,w)->openStore())
                    .setOnCancelListener(d->finish()).show();
            return;
        }
        if(attempted) return;
        attempted=true;
        try {
            Intent install = new Intent(this, InstallProgressActivity.class);
            install.putExtra(EXTRA_PACKAGE, packageName);
            install.putExtra(EXTRA_LABEL, label);
            install.setClipData(sources);
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(install);
            finish();
        } catch(RuntimeException e) {
            attempted=false;
            error("Android no pudo abrir el instalador: " + e.getMessage());
        }
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==REQUEST_SOURCE_PERMISSION){
            if(getPackageManager().canRequestPackageInstalls())checkAndInstall();
            else new AlertDialog.Builder(this).setMessage("No se autorizó la instalación en el perfil de trabajo.")
                    .setPositiveButton("Abrir Play Store",(d,w)->openStore())
                    .setNegativeButton("Cerrar",(d,w)->finish()).show();
        }
    }
    private void openStore(){
        try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("market://details?id="+packageName)));}
        catch(RuntimeException ex){
            try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://play.google.com/store/apps/details?id="+packageName)));}
            catch(RuntimeException ignored){Toast.makeText(this,"No hay tienda disponible.",Toast.LENGTH_LONG).show();}
        }
        finish();
    }
    private void error(String s){new AlertDialog.Builder(this).setMessage(s).setPositiveButton("Cerrar",(d,w)->finish()).show();}
}
