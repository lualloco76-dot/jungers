package com.duospace.lite;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Bundle;

public final class InstallStatusActivity extends Activity {
    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        handle(getIntent());
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handle(intent);
    }
    private void handle(Intent intent) {
        int status=intent.getIntExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE);
        String app=intent.getStringExtra("target_package");
        if(status==PackageInstaller.STATUS_PENDING_USER_ACTION){
            Intent confirmation;
            if(android.os.Build.VERSION.SDK_INT>=33){
                confirmation=intent.getParcelableExtra(Intent.EXTRA_INTENT,Intent.class);
            }else{
                confirmation=intent.getParcelableExtra(Intent.EXTRA_INTENT);
            }
            if(confirmation!=null){
                try{startActivityForResult(confirmation,111);return;}
                catch(RuntimeException ignored){}
            }
            show("Instalación pendiente","No se pudo mostrar la autorización de Android.");
        } else if(status==PackageInstaller.STATUS_SUCCESS) {
            show("Copia instalada","La nueva instalación ya está disponible en el perfil DuoSpace. Podrás iniciar sesión con otra cuenta.");
        } else {
            String reason=intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
            show("Instalación fallida", "Android rechazó la instalación"+
                    (reason==null?".":": "+reason)+". Puedes intentar instalar desde Play Store dentro del perfil de trabajo.");
        }
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==111){
            if(resultCode==RESULT_OK) show("Solicitud procesada","Comprueba la instalación en el perfil de trabajo.");
            else show("Instalación cancelada","No se completó la instalación.");
        }
    }
    private void show(String title,String msg){
        new AlertDialog.Builder(this).setTitle(title).setMessage(msg)
                .setPositiveButton("Abrir DuoSpace",(d,w)->{
                    startActivity(new Intent(this,MainActivity.class));finish();
                }).setNegativeButton("Cerrar",(d,w)->finish()).setOnCancelListener(d->finish()).show();
    }
}
