package co.com.jongers.print;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.InputFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;

public final class MainActivity extends Activity {
    private static final String USB_PERMISSION="co.com.jongers.print.USB_PERMISSION";
    private final int BG=Color.rgb(15,20,30),PANEL=Color.rgb(29,36,48),YELLOW=Color.rgb(255,203,50);
    private TextView status,devices;
    private LinearLayout root;
    private ScrollView screen;
    private final BroadcastReceiver usbPermissionReceiver=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent intent){
            if(!USB_PERMISSION.equals(intent.getAction()))return;
            boolean granted=intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED,false);
            Toast.makeText(c,granted?"USB autorizado":"Permiso USB denegado",Toast.LENGTH_LONG).show();refresh();
        }
    };
    @Override public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(usbPermissionReceiver,new IntentFilter(USB_PERMISSION),Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(usbPermissionReceiver,new IntentFilter(USB_PERMISSION));
        screen=new ScrollView(this);screen.setFillViewport(true);screen.setBackgroundColor(BG);
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(19),dp(23),dp(19),dp(35));
        screen.addView(root);setContentView(screen);
        showSteps();
    }
    private int dp(int value){return (int)(value*getResources().getDisplayMetrics().density+0.5f);}
    private GradientDrawable panelDrawable(int color,int radius){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;
    }
    private LinearLayout card(){
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(18),dp(16),dp(18),dp(18));c.setBackground(panelDrawable(PANEL,18));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(11),0,dp(4));root.addView(c,lp);return c;
    }
    private void detail(LinearLayout parent,String txt){
        TextView v=label(parent,txt,14,0xffb8c2d3,false);v.setLineSpacing(dp(3),1f);
    }
    private void stepTitle(LinearLayout parent,String title){
        TextView v=label(parent,title,19,Color.WHITE,true);v.setPadding(0,dp(5),0,dp(7));
    }
    private boolean paired(){
        try{return !SecureStore.read(this).isEmpty();}
        catch(Exception e){return false;}
    }
    private void showSteps(){
        root.removeAllViews();status=null;devices=null;
        label(root,"JONGER'S",33,YELLOW,true);
        label(root,"PRINT LIBRE  ·  GRATIS  ·  SIN LIMITE DE TICKETS",13,0xffd4dbe6,true);
        detail(root,"Servicio ESC/POS sin pagos por impresion. Imprime desde Android por USB, Bluetooth o Wi-Fi. El enlace remoto a FuegoPOS es opcional.");
        LinearLayout local=card();
        stepTitle(local,"1  ·  IMPRESION DEL SISTEMA ANDROID");
        detail(local,"Esta funcion NO requiere codigo de 10 digitos ni conexion a la API. Configura la impresora mas abajo y activa el servicio en Ajustes de Android. Luego usa Imprimir desde Chrome.");
        button(local,"ACTIVAR IMPRESION EN ANDROID",this::openSystemPrinting);
        detail(local,"Desde la pantalla de impresion elige Jonger's Print Libre. Android puede pedir confirmar cada ticket. Las comandas remotas automaticas son un modo separado.");
        long printed=SecureStore.publicPrefs(this).getLong("system_print_count",0L);
        label(local,"Tickets por Android enviados: "+printed+" · Sin cobro ni caducidad",14,YELLOW,true);
        boolean ready=paired();
        LinearLayout intro=card();
        stepTitle(intro,ready?"FUEGOPOS REMOTO · VINCULADO":"2  ·  ENLACE REMOTO FUEGOPOS (OPCIONAL)");
        if(!ready){
            detail(intro,"Solo para recibir COMANDAS AUTOMATICAS enviadas desde otros equipos. En FuegoPOS > Equipos e impresoras genera el codigo (10 minutos). Si Imunify360 bloquea el enlace, puedes seguir imprimiendo localmente desde Android.");
            EditText code=new EditText(this);code.setSingleLine(true);code.setTextSize(23);
            code.setTextColor(Color.WHITE);code.setHintTextColor(0xff7c8b9e);
            code.setHint("Codigo de 10 digitos");code.setInputType(InputType.TYPE_CLASS_NUMBER);
            code.setFilters(new InputFilter[]{new InputFilter.LengthFilter(10)});
            code.setPadding(dp(14),dp(11),dp(14),dp(11));code.setBackground(panelDrawable(BG,10));
            intro.addView(code,new LinearLayout.LayoutParams(-1,dp(56)));
            button(intro,"COMPROBAR CONEXION CON FUEGOPOS",()->{
                new Thread(()->{
                    try{
                        String result=PrintService.diagnose(this);
                        runOnUiThread(()->message("Servidor disponible",result+"\nYa puedes introducir el codigo de 10 digitos."));
                    }catch(Exception ex){
                        runOnUiThread(()->message("Problema con el servidor",ex.getMessage()));
                    }
                },"jongers-test-api").start();
            });
            button(intro,"CONECTAR ESTE ANDROID",()->{
                String value=code.getText().toString().trim();
                if(!value.matches("[0-9]{10}")){message("Codigo incompleto","Escribe los 10 digitos mostrados en FuegoPOS.");return;}
                code.setEnabled(false);
                new Thread(()->{
                    try{
                        org.json.JSONObject response=PrintService.pair(this,value);
                        if(!response.optBoolean("ok",false))throw new Exception(response.optString("error","No se pudo vincular"));
                        SecureStore.save(this,response.getString("token"));
                        String name=response.optString("terminal_name","Android Jonger's");
                        runOnUiThread(()->{toast("Vinculado: "+name);showSteps();});
                    }catch(Exception ex){runOnUiThread(()->{code.setEnabled(true);message("No se pudo conectar",ex.getMessage());});}
                },"jongers-pair").start();
            });
            detail(intro,"El codigo se utiliza una sola vez. La aplicacion guarda la autorizacion de manera cifrada.");
            label(root,"La impresora local se puede usar sin conectar con FuegoPOS.",13,0xff8290a4,false);
        } else {
            detail(intro,"Este dispositivo ya esta autorizado. No necesitas escribir otra clave al imprimir.");
        }
        LinearLayout printer=card();
        stepTitle(printer,"3  ·  ELEGIR IMPRESORA");
        detail(printer,"Primero empareja la impresora desde Ajustes Bluetooth de Android. Despues seleccionala aqui.");
        button(printer,"BUSCAR IMPRESORA BLUETOOTH",this::chooseBluetooth);
        button(printer,"Usar impresora USB",this::allowUsb);
        button(printer,"Usar impresora Wi-Fi",this::addWifi);
        button(printer,"Ancho de papel (58 / 80 mm)",this::choosePaper);
        devices=label(printer,"Buscando impresoras...",13,0xffc8d1df,false);
        LinearLayout test=card();
        stepTitle(test,"4  ·  PROBAR EL TICKET");
        detail(test,"Comprueba que salga el papel correcto antes de recibir pedidos de cocina.");
        button(test,"IMPRIMIR TICKET DE PRUEBA",this::testPrinter);
        LinearLayout auto=card();
        stepTitle(auto,"5  ·  COMANDAS REMOTAS AUTOMATICAS");
        detail(auto,"Opcion para pedidos enviados desde otros dispositivos. Requiere vincular el Android a FuegoPOS y que el hosting no bloquee la API. No es necesaria para imprimir localmente desde Android.");
        button(auto,"ACTIVAR RECEPCION AUTOMATICA DE COMANDAS",()->{
            if(!paired()){message("Falta vinculacion remota","Para recibir pedidos de otros meseros debes vincular este equipo a FuegoPOS. Para imprimir desde Chrome solo activa el Servicio de impresion de Android.");return;}
            try{
                askPermissions();
                Intent i=new Intent(this,PrintService.class);i.setAction(PrintService.ACTION_START);
                if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);
                toast("Servicio activado. Configura Cocina en el panel web.");refresh();
            }catch(Exception e){message("No se puede iniciar",e.getMessage());}
        });
        button(auto,"Actualizar estado",this::refresh);
        status=label(auto,"Verificando estado del servicio...",13,Color.WHITE,false);
        detail(auto,"En FuegoPOS > Equipos e impresoras, elige la impresora detectada para Cocina y para el recibo. Si solo hay una, se podra usar en ambos.");
        button(root,"Detener impresion automatica",()->{
            Intent i=new Intent(this,PrintService.class);i.setAction(PrintService.ACTION_STOP);
            startService(i);toast("Servicio detenido");refresh();
        });
        refresh();
    }
    private TextView label(LinearLayout root,String text,int size,int color,boolean bold){
        TextView v=new TextView(this);v.setText(text);v.setTextColor(color);v.setTextSize(size);v.setPadding(3,7,3,7);
        if(bold)v.setTypeface(null,1);root.addView(v);return v;
    }
    private void space(LinearLayout root,int dp){View v=new View(this);root.addView(v,new LinearLayout.LayoutParams(1,(int)(dp*getResources().getDisplayMetrics().density)));}
    private void button(LinearLayout root,String title,Runnable action){
        Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setTextColor(BG);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(YELLOW));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=8;root.addView(b,lp);
        b.setOnClickListener(v->action.run());
    }
    private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
    private void message(String title,String body){new AlertDialog.Builder(this).setTitle(title).setMessage(body).setPositiveButton("Entendido",null).show();}
    private void askPermissions(){
        if(Build.VERSION.SDK_INT>=33){
            boolean bt=checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED;
            boolean notif=checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED;
            if(!bt||!notif)requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.POST_NOTIFICATIONS},501);
        }else if(Build.VERSION.SDK_INT>=31&&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},501);
        }
    }
    private void chooseBluetooth(){
        try{
            if(!PrinterConnections.bluetoothAllowed(this)){askPermissions();toast("Concede permiso Bluetooth y vuelve a tocar Elegir");return;}
            android.bluetooth.BluetoothManager manager=(android.bluetooth.BluetoothManager)getSystemService(BLUETOOTH_SERVICE);
            android.bluetooth.BluetoothAdapter adapter=manager==null?null:manager.getAdapter();
            if(adapter==null||adapter.getBondedDevices().isEmpty()){
                message("Emparejamiento necesario","Primero empareja la impresora Bluetooth en Ajustes de Android. Debe ser Bluetooth clásico SPP.");return;
            }
            java.util.List<android.bluetooth.BluetoothDevice> paired=new java.util.ArrayList<>(adapter.getBondedDevices());
            java.util.List<String> labels=new java.util.ArrayList<>();
            for(android.bluetooth.BluetoothDevice d:paired)labels.add((d.getName()==null?"Dispositivo":d.getName())+" · "+d.getAddress());
            new AlertDialog.Builder(this).setTitle("Escoge tu impresora Bluetooth")
                .setItems(labels.toArray(new String[0]),(dialog,index)->{
                    String mac=paired.get(index).getAddress().toUpperCase(java.util.Locale.ROOT);
                    SecureStore.publicPrefs(this).edit().putString("chosen_bluetooth_printer","BT:"+mac).apply();
                    toast("Impresora seleccionada: "+mac);showSteps();
                }).setNegativeButton("Cancelar",null).show();
        }catch(Exception e){message("Bluetooth",e.getMessage());}
    }
    private void testPrinter(){
        java.util.List<String> list=PrinterConnections.discover(this);
        if(list.isEmpty()){message("Sin impresoras","Selecciona una Bluetooth, autoriza una USB o agrega una impresora Wi-Fi.");return;}
        new AlertDialog.Builder(this).setTitle("Prueba de impresión")
            .setItems(list.toArray(new String[0]),(d,index)->{
                final String id=list.get(index);
                new Thread(()->{
                    try{
                        // Probar con la propia codificación ESC/POS, sin incluir datos de clientes.
                        byte[] bytes=("\u001B@\u001Ba\u0001JONGERS\nPRUEBA DE IMPRESION\n"+
                          "Conexion: "+id+"\n\n\n\u001DV\u0000").getBytes(java.nio.charset.StandardCharsets.US_ASCII);
                        PrinterConnections.print(this,id,bytes);
                        runOnUiThread(()->toast("Bytes enviados; comprueba que salió el papel."));
                    }catch(Exception ex){runOnUiThread(()->message("Prueba fallida",ex.getMessage()));}
                },"jongers-print-test").start();
            }).setNegativeButton("Cancelar",null).show();
    }
    private void allowUsb(){
        UsbManager usb=(UsbManager)getSystemService(USB_SERVICE);
        if(usb==null||usb.getDeviceList().isEmpty()){toast("Conecta la impresora USB con un adaptador OTG");return;}
        int flags=PendingIntent.FLAG_UPDATE_CURRENT;
        if(Build.VERSION.SDK_INT>=31)flags|=PendingIntent.FLAG_MUTABLE;
        Intent grant=new Intent(USB_PERMISSION);grant.setPackage(getPackageName());
        PendingIntent pi=PendingIntent.getBroadcast(this,77,grant,flags);
        for(UsbDevice device:usb.getDeviceList().values())if(!usb.hasPermission(device))usb.requestPermission(device,pi);
        toast("Revisa el permiso de acceso USB en pantalla");refresh();
    }
    private void addWifi(){
        LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(25,10,25,0);
        EditText ip=new EditText(this);ip.setSingleLine(true);ip.setHint("IP: 192.168.1.100");form.addView(ip);
        EditText port=new EditText(this);port.setInputType(InputType.TYPE_CLASS_NUMBER);port.setText("9100");form.addView(port);
        new AlertDialog.Builder(this).setTitle("Impresora ESC/POS por red")
            .setMessage("Introduce la IP que tiene la impresora en la misma red local del Android")
            .setView(form).setPositiveButton("Guardar",(d,w)->{
                try{PrinterConnections.addTcp(this,ip.getText().toString().trim(),Integer.parseInt(port.getText().toString().trim()));showSteps();toast("Impresora guardada. Inicia el servicio para sincronizar.");}
                catch(Exception e){message("Datos inválidos",e.getMessage());}
            }).setNegativeButton("Cancelar",null).show();
    }
    private void openSystemPrinting(){
        try{startActivity(new Intent(android.provider.Settings.ACTION_PRINT_SETTINGS));}
        catch(Exception e){message("Ajustes de impresion","En Ajustes de Android busca Impresion, Servicios de impresion y activa Jonger's Print Libre.");}
    }
    private void choosePaper(){
        String[] modes={"58 mm · 384 puntos","80 mm · 576 puntos"};
        int current=SecureStore.publicPrefs(this).getInt("paper_width_dots",384)==576?1:0;
        new AlertDialog.Builder(this).setTitle("Ancho del papel termico")
            .setSingleChoiceItems(modes,current,(d,i)->{
                SecureStore.publicPrefs(this).edit().putInt("paper_width_dots",i==1?576:384).apply();
                d.dismiss();toast("Ancho guardado. Si el servicio ya esta activo, vuelve a abrir el menu de impresion para actualizar.");
            }).setNegativeButton("Cancelar",null).show();
    }
    private void refresh(){
        if(status!=null){
            long seen=SecureStore.publicPrefs(this).getLong("last_seen_at",0);
            String last=SecureStore.publicPrefs(this).getString("last_status","Pulsa ACTIVAR IMPRESION AUTOMATICA.");
            status.setText(last+(seen>0?"\nUltima actividad: "+android.text.format.DateFormat.format("HH:mm:ss",seen):""));
        }
        if(devices!=null){
            List<String> list=PrinterConnections.discover(this);
            devices.setText(list.isEmpty()?"No hay impresora seleccionada. Empareja Bluetooth o autoriza USB.":"Detectadas: "+android.text.TextUtils.join("  |  ",list));
        }
    }
    @Override protected void onResume(){super.onResume();refresh();}
    @Override protected void onDestroy(){unregisterReceiver(usbPermissionReceiver);super.onDestroy();}
}
