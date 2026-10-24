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
    private EditText tokenField;private TextView status,devices;
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
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);
        LinearLayout main=new LinearLayout(this);main.setOrientation(LinearLayout.VERTICAL);main.setPadding(20,28,20,30);scroll.addView(main);
        label(main,"JONGER'S",30,YELLOW,true);
        label(main,"AGENTE DE IMPRESIÓN ANDROID · ESC/POS",14,Color.WHITE,true);
        label(main,"Impresión automática: Bluetooth clásico, USB OTG y Wi-Fi/Ethernet. Mantén activo este equipo durante el servicio.",14,0xffb8c2d3,false);
        space(main,16);
        label(main,"CREDENCIAL DEL EQUIPO",14,YELLOW,true);
        tokenField=new EditText(this);tokenField.setSingleLine(true);
        tokenField.setTextColor(Color.WHITE);tokenField.setHintTextColor(0xff8995a8);tokenField.setHint("Pega los 64 caracteres generados en FuegoPOS");
        tokenField.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        tokenField.setBackgroundColor(PANEL);tokenField.setPadding(12,12,12,12);main.addView(tokenField);
        button(main,"Guardar clave de esta terminal",()->{
            try{SecureStore.save(this,tokenField.getText().toString().trim());tokenField.setText("");toast("Credencial guardada de manera cifrada");refresh();}
            catch(Exception ex){message("No se guardó",ex.getMessage());}
        });
        button(main,"Solicitar permisos Bluetooth y avisos",this::askPermissions);
        button(main,"Elegir impresora Bluetooth emparejada",this::chooseBluetooth);
        button(main,"Autorizar impresoras USB conectadas",this::allowUsb);
        button(main,"Agregar impresora Wi-Fi (IP/puerto)",this::addWifi);
        space(main,15);
        button(main,"INICIAR IMPRESIÓN AUTOMÁTICA",()->{
            try{
                if(SecureStore.read(this).isEmpty()){toast("Primero guarda la clave de la terminal");return;}
                askPermissions();Intent i=new Intent(this,PrintService.class);i.setAction(PrintService.ACTION_START);
                if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);
                toast("Servicio activado; verifica la notificación permanente");refresh();
            }catch(Exception e){message("No se puede iniciar",e.getMessage());}
        });
        button(main,"Detener servicio",()->{Intent i=new Intent(this,PrintService.class);i.setAction(PrintService.ACTION_STOP);startService(i);toast("Servicio detenido");refresh();});
        button(main,"Actualizar lista de impresoras y estado",this::refresh);
        button(main,"IMPRIMIR TICKET DE PRUEBA",this::testPrinter);
        space(main,14);
        label(main,"ESTADO DEL SERVICIO",14,YELLOW,true);
        status=label(main,"Sin conexión",15,Color.WHITE,false);
        space(main,12);
        label(main,"IMPRESORAS DISPONIBLES EN ANDROID",14,YELLOW,true);
        devices=label(main,"-",15,Color.WHITE,false);
        label(main,"Después de iniciar el servicio, entra en Jonger's POS > Equipos e impresoras y asigna Cocina y Cliente. Si hay una sola impresora, el sistema puede asignarla automáticamente.",13,0xffb8c2d3,false);
        setContentView(scroll);refresh();
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
                    toast("Impresora seleccionada: "+mac);refresh();
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
                try{PrinterConnections.addTcp(this,ip.getText().toString().trim(),Integer.parseInt(port.getText().toString().trim()));refresh();toast("Impresora guardada. Inicia el servicio para sincronizar.");}
                catch(Exception e){message("Datos inválidos",e.getMessage());}
            }).setNegativeButton("Cancelar",null).show();
    }
    private void refresh(){if(status==null||devices==null)return;
        long seen=SecureStore.publicPrefs(this).getLong("last_seen_at",0);
        String last=SecureStore.publicPrefs(this).getString("last_status","Inicia el servicio para conectar");
        status.setText(last+(seen>0?"\nÚltimo ciclo: "+android.text.format.DateFormat.format("HH:mm:ss",seen):""));
        List<String> list=PrinterConnections.discover(this);
        devices.setText(list.isEmpty()?"No hay impresoras. Empareja Bluetooth, autoriza USB o agrega IP Wi-Fi.":android.text.TextUtils.join("\n",list));
    }
    @Override protected void onResume(){super.onResume();refresh();}
    @Override protected void onDestroy(){unregisterReceiver(usbPermissionReceiver);super.onDestroy();}
}
