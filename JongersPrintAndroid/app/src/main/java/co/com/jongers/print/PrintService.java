package co.com.jongers.print;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PrintService extends Service {
    static final String URL_API="https://jongers.com.co/public/terminal_api.php";
    static final String URL_API_ALT="https://jongers.com.co/terminal_api.php";
    static final String ACTION_START="co.com.jongers.print.START";
    static final String ACTION_STOP="co.com.jongers.print.STOP";
    static final String CHANNEL="jongers_print_service";
    private volatile boolean working=false;
    private Thread worker;
    private final Object mutex=new Object();
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null&&ACTION_STOP.equals(intent.getAction())){
            working=false;if(worker!=null)worker.interrupt();
            if(Build.VERSION.SDK_INT>=24)stopForeground(STOP_FOREGROUND_REMOVE);else stopForeground(true);
            stopSelf();return START_NOT_STICKY;
        }
        startVisible();
        synchronized(mutex){
            if(!working){working=true;worker=new Thread(this::runLoop,"jongers-print-poll");worker.start();}
        }
        return START_STICKY;
    }
    private void startVisible(){
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(
            new NotificationChannel(CHANNEL,"Impresión Jonger's",NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder builder=Build.VERSION.SDK_INT>=26
                ? new Notification.Builder(this,CHANNEL) : new Notification.Builder(this);
        Notification n=builder.setContentTitle("Jonger's · Impresión automática")
                .setContentText("Esperando comandas de FuegoPOS")
                .setSmallIcon(android.R.drawable.ic_menu_info_details).setOngoing(true).setContentIntent(open).build();
        if(Build.VERSION.SDK_INT>=34)startForeground(513,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        else startForeground(513,n);
    }
    static void updateStatus(Service s,String message){
        SecureStore.publicPrefs(s).edit().putString("last_status",message)
            .putLong("last_seen_at",System.currentTimeMillis()).apply();
    }
    private void runLoop(){
        long discovery=0;
        while(working&&!Thread.currentThread().isInterrupted()){
            try{
                String token=SecureStore.read(this);
                if(token.isEmpty()){updateStatus(this,"Configura la clave del equipo.");working=false;break;}
                if(SystemClock.elapsedRealtime()-discovery>15000||discovery==0){
                    JSONArray list=new JSONArray();for(String name:PrinterConnections.discover(this))list.put(name);
                    JSONObject result=call(this,token,map("action","discover","printers",list.toString()));
                    if(!result.optBoolean("ok",false))throw new Exception("No se pudo sincronizar impresoras");
                    discovery=SystemClock.elapsedRealtime();
                }
                // Resolver confirmación pendiente antes de pedir otro trabajo.
                flushAcknowledgement(token);
                JSONObject job=call(this,token,map("action","poll"));
                String state=job.optString("state","");
                if("job".equals(state))process(token,job);
                else if("configuration_required".equals(state))updateStatus(this,"Elige las impresoras en Equipos e impresoras (FuegoPOS).");
                else if("printer_disconnected".equals(state))updateStatus(this,"Impresora desconectada. Ticket conservado en la cola.");
                else if("idle".equals(state))updateStatus(this,"Conectado. Esperando nuevos tickets.");
            }catch(Exception e){updateStatus(this,"Aviso: "+safe(e.getMessage()));}
            try{Thread.sleep(3500);}catch(InterruptedException e){Thread.currentThread().interrupt();}
        }
        if(!working)stopSelf();
    }
    private void process(String token,JSONObject job){
        int id=job.optInt("job_id",0);String lease=job.optString("lease","");
        String error=""; boolean ok=false;
        if(id<=0||!lease.matches("[0-9a-f]{48}")){updateStatus(this,"Respuesta de cola inválida");return;}
        SharedPreferences p=SecureStore.publicPrefs(this);
        try{
            // Evita imprimir de nuevo un trabajo transmitido anteriormente (por pérdida del ACK).
            String journal=p.getString("printed_job_ids",",");
            if(journal.contains(","+id+","))ok=true;
            else{
                byte[] bytes=Base64.decode(job.getString("data"),Base64.DEFAULT);
                if(bytes.length==0||bytes.length>2*1024*1024)throw new Exception("Ticket demasiado grande");
                JSONObject route=job.getJSONObject("route");
                String mode=route.optString("mode","");
                if("android".equals(mode))PrinterConnections.print(this,route.getString("printer_name"),bytes);
                else if("tcp".equals(mode))PrinterConnections.printTcp(route.getString("host"),route.optInt("port",9100),bytes);
                else throw new Exception("Modo no compatible con Android: "+mode);
                // Persistir la constancia antes de informar al servidor; reduce duplicados.
                journal+=id+",";
                if(journal.length()>3800)journal=journal.substring(journal.length()-3500);
                if(!journal.startsWith(","))journal=","+journal.substring(journal.indexOf(',')+1);
                if(!p.edit().putString("printed_job_ids",journal).commit())throw new Exception("No se pudo guardar confirmación local");
                ok=true;
            }
            updateStatus(this,"Ticket #"+id+" transmitido a la impresora.");
        }catch(Exception ex){error=safe(ex.getMessage());updateStatus(this,"Ticket #"+id+" falló: "+error);}
        JSONObject ack=new JSONObject();
        try{
            ack.put("job_id",id);ack.put("lease",lease);ack.put("ok",ok?"1":"0");ack.put("error",error);
            // ACK pendiente se guarda antes de transmitir. Se reenvía si se cae internet.
            p.edit().putString("pending_ack",ack.toString()).commit();
            flushAcknowledgement(token);
        }catch(Exception e){updateStatus(this,"Pendiente confirmar ticket #"+id+": "+safe(e.getMessage()));}
    }
    private void flushAcknowledgement(String token) throws Exception {
        SharedPreferences prefs=SecureStore.publicPrefs(this);
        String raw=prefs.getString("pending_ack","");if(raw.isEmpty())return;
        JSONObject obj=new JSONObject(raw);
        Map<String,String> params=map("action","ack","job_id",String.valueOf(obj.optInt("job_id")),
            "lease",obj.optString("lease"),"ok",obj.optString("ok"),"error",obj.optString("error"));
        try{JSONObject response=call(this,token,params);
            if(response.optBoolean("ok",false))prefs.edit().remove("pending_ack").commit();
        }catch(ServerError e){
            // Un ACK caducado ya no se puede reutilizar. El administrador revisará ese trabajo.
            if(e.status==409){prefs.edit().remove("pending_ack").commit();updateStatus(this,"ACK caducado; revisar historial de impresión.");}
            else throw e;
        }
    }
    private static String safe(String m){
        if(m==null)return "Error sin detalle";
        return m.length()>150?m.substring(0,150):m;
    }
    static Map<String,String> map(String... parts){
        Map<String,String> m=new LinkedHashMap<>();for(int i=0;i+1<parts.length;i+=2)m.put(parts[i],parts[i+1]);return m;
    }
    static final class ServerError extends Exception {
        final int status; ServerError(int status,String msg){super("HTTP "+status+": "+msg);this.status=status;}
    }
    static final class RouteError extends Exception {
        RouteError(String message){super(message);}
    }
    static JSONObject pair(android.content.Context context,String code) throws Exception {
        StringBuilder failures=new StringBuilder();
        for(String endpoint:new String[]{URL_API,URL_API_ALT}){
            try{
                JSONObject response=request(endpoint,"",map("action","pair","code",code));
                if(response.optBoolean("ok",false)){
                    SecureStore.publicPrefs(context).edit().putString("api_endpoint",endpoint).apply();
                }
                return response;
            }catch(RouteError e){
                failures.append("\n").append(endpoint).append(" — ").append(e.getMessage());
            }catch(ServerError e){
                if(e.status!=404 && e.status!=405 && e.status!=502 && e.status!=503)throw e;
                failures.append("\n").append(endpoint).append(" — HTTP ").append(e.status);
            }
        }
        throw new Exception("FuegoPOS no responde como API en ninguna de las dos rutas. "
            +"Comprueba que public/terminal_api.php esté instalado en cPanel y que no haya "
            +"una redirección de acceso o bloqueo del servidor."+failures);
    }
    static String diagnose(android.content.Context context) throws Exception {
        StringBuilder failures=new StringBuilder();
        for(String endpoint:new String[]{URL_API,URL_API_ALT}){
            try{
                request(endpoint,"",map("action","pair","code","0"));
                SecureStore.publicPrefs(context).edit().putString("api_endpoint",endpoint).apply();
                return "API disponible: "+endpoint;
            }catch(ServerError e){
                if(e.status==400 && e.getMessage().contains("10")){
                    SecureStore.publicPrefs(context).edit().putString("api_endpoint",endpoint).apply();
                    return "API funcionando: "+endpoint;
                }
                failures.append("\n").append(endpoint).append(" — HTTP ").append(e.status);
            }catch(RouteError e){
                failures.append("\n").append(endpoint).append(" — ").append(e.getMessage());
            }
        }
        throw new Exception("El servidor no está entregando JSON. Reinstala el archivo "
            +"public/terminal_api.php desde la actualización 22 y revisa la ruta en el navegador."+failures);
    }
    static JSONObject call(android.content.Context context,String token,Map<String,String> fields)throws Exception{
        String endpoint=SecureStore.publicPrefs(context).getString("api_endpoint",URL_API);
        if(!URL_API.equals(endpoint)&&!URL_API_ALT.equals(endpoint))endpoint=URL_API;
        return request(endpoint,token,fields);
    }
    private static JSONObject request(String endpoint,String token,Map<String,String> fields)throws Exception{
        HttpURLConnection conn=null;
        try{
            conn=(HttpURLConnection)new URL(endpoint).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(8000);conn.setReadTimeout(20000);
            conn.setDoOutput(true);conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("Content-Type","application/x-www-form-urlencoded; charset=utf-8");
            conn.setRequestProperty("Accept","application/json");
            if(!token.isEmpty())conn.setRequestProperty("X-Jongers-Print-Token",token);
            StringBuilder body=new StringBuilder();
            for(Map.Entry<String,String> field:fields.entrySet()){
                if(body.length()>0)body.append('&');
                body.append(URLEncoder.encode(field.getKey(),"UTF-8"));body.append('=');
                body.append(URLEncoder.encode(field.getValue(),"UTF-8"));
            }
            byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(bytes.length);
            try(OutputStream out=conn.getOutputStream()){out.write(bytes);}
            int status=conn.getResponseCode();
            if(status>=300&&status<400)
                throw new RouteError("HTTP "+status+": el hosting está redirigiendo la solicitud.");
            InputStream stream=status>=200&&status<300?conn.getInputStream():conn.getErrorStream();
            if(stream==null)throw new RouteError("HTTP "+status+": respuesta vacía.");
            ByteArrayOutputStream response=new ByteArrayOutputStream();
            try(InputStream in=stream){
                byte[] buf=new byte[8192];int size;
                while((size=in.read(buf))!=-1){
                    if(response.size()+size>4*1024*1024)throw new Exception("Respuesta demasiado grande");
                    response.write(buf,0,size);
                }
            }
            String raw=response.toString("UTF-8").trim();
            if(raw.startsWith("<")||raw.startsWith("<!")){
                throw new RouteError("HTTP "+status+": el servidor devolvió HTML, no JSON "
                    +"(página de error, login o protección web).");
            }
            JSONObject obj;
            try{obj=new JSONObject(raw);}
            catch(org.json.JSONException e){
                throw new RouteError("HTTP "+status+": respuesta de API no válida.");
            }
            if(status!=200)throw new ServerError(status,obj.optString("error","Error del servidor"));
            return obj;
        }finally{if(conn!=null)conn.disconnect();}
    }

    @Override public void onDestroy(){working=false;if(worker!=null)worker.interrupt();super.onDestroy();}
}
