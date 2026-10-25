package co.com.jongers.print;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * Recupera el servicio de recepción remota después del arranque o una actualización.
 * No vuelve a emparejar el dispositivo ni modifica la credencial guardada.
 */
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        if(intent==null)return;
        String action=intent.getAction();
        if(!Intent.ACTION_BOOT_COMPLETED.equals(action)
            && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action))return;
        if(!SecureStore.publicPrefs(context).getBoolean("auto_start_printing",false))return;
        try{
            if(SecureStore.read(context).isEmpty())return;
            Intent service=new Intent(context,PrintService.class);
            service.setAction(PrintService.ACTION_START);
            if(Build.VERSION.SDK_INT>=26)context.startForegroundService(service);
            else context.startService(service);
        }catch(Exception error){
            PrintService.markServerFailure(context,"Autoinicio pendiente: "+error.getMessage());
        }
    }
}
