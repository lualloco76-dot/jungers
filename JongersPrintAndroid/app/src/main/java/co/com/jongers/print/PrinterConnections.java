package co.com.jongers.print;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Build;
import org.json.JSONArray;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class PrinterConnections {
    static final UUID SPP=UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
    static boolean bluetoothAllowed(Context c) {return Build.VERSION.SDK_INT<31 || c.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED;}
    static List<String> discover(Context c) {
        Set<String> ids=new HashSet<>();
        try {
            BluetoothManager mgr=(BluetoothManager)c.getSystemService(Context.BLUETOOTH_SERVICE);
            BluetoothAdapter adapter=mgr==null?null:mgr.getAdapter();
            // No enviar auriculares u otros Bluetooth emparejados como "impresoras".
            // El operador identifica primero su impresora desde esta aplicación.
            String approved=SecureStore.publicPrefs(c).getString("chosen_bluetooth_printer", "");
            if(adapter!=null && bluetoothAllowed(c))for(BluetoothDevice device:adapter.getBondedDevices()) {
                String address=device.getAddress().toUpperCase(java.util.Locale.ROOT);
                if(approved.equals("BT:"+address))ids.add(approved);
            }
        }catch(SecurityException ignored){}
        UsbManager usb=(UsbManager)c.getSystemService(Context.USB_SERVICE);
        if(usb!=null)for(UsbDevice device:usb.getDeviceList().values()){
            if(hasBulkOut(device))ids.add("USB:"+device.getVendorId()+":"+device.getProductId());
        }
        JSONArray configured=networkProfiles(c);
        for(int i=0;i<configured.length();i++){
            String key=configured.optString(i,"");
            if(isValidTcpId(key))ids.add(key);
        }
        List<String> result=new ArrayList<>(ids);Collections.sort(result);
        return result.size()>40 ? result.subList(0,40) : result;
    }
    private static boolean hasBulkOut(UsbDevice device){
        for(int i=0;i<device.getInterfaceCount();i++){
            UsbInterface face=device.getInterface(i);
            for(int j=0;j<face.getEndpointCount();j++){
                UsbEndpoint ep=face.getEndpoint(j);
                if(ep.getDirection()==UsbConstants.USB_DIR_OUT && ep.getType()==UsbConstants.USB_ENDPOINT_XFER_BULK) return true;
            }
        }
        return false;
    }
    static JSONArray networkProfiles(Context c){
        try{return new JSONArray(SecureStore.publicPrefs(c).getString("net_printers","[]"));}
        catch(Exception e){return new JSONArray();}
    }
    static boolean isValidTcpId(String id){
        if(!id.startsWith("TCP:"))return false;
        String[] parts=id.split(":");
        if(parts.length!=3||!isIPv4(parts[1]))return false;
        try{int p=Integer.parseInt(parts[2]);return p>=1&&p<=65535;}catch(NumberFormatException e){return false;}
    }
    static boolean isIPv4(String host){
        String[] parts=host.split("\\.", -1);
        if(parts.length!=4)return false;
        for(String part:parts){
            if(part.isEmpty()||part.length()>3||!part.matches("[0-9]+"))return false;
            int value=Integer.parseInt(part);if(value>255)return false;
        }
        return true;
    }
    static void addTcp(Context c,String ip,int port) throws Exception {
        if(!isIPv4(ip)||port<1||port>65535)throw new Exception("IP o puerto inválido");
        String id="TCP:"+ip+":"+port;
        JSONArray old=networkProfiles(c);
        for(int i=0;i<old.length();i++)if(id.equals(old.optString(i)))return;
        if(old.length()>=20)throw new Exception("Máximo de impresoras de red configuradas.");
        old.put(id);
        SecureStore.publicPrefs(c).edit().putString("net_printers",old.toString()).apply();
    }
    static void print(Context c,String id,byte[] bytes) throws Exception {
        if(bytes==null||bytes.length==0||bytes.length>2*1024*1024)throw new Exception("Datos de impresión inválidos");
        if(id.startsWith("BT:")){printBluetooth(c,id.substring(3),bytes);return;}
        if(id.startsWith("USB:")){printUsb(c,id,bytes);return;}
        if(isValidTcpId(id)){String[] parts=id.split(":");printTcp(parts[1],Integer.parseInt(parts[2]),bytes);return;}
        throw new Exception("Impresora no configurada: "+id);
    }
    private static void printBluetooth(Context c,String mac,byte[] data) throws Exception {
        if(!bluetoothAllowed(c))throw new Exception("Falta permiso Bluetooth en Android");
        if(!mac.matches("(?i)([0-9A-F]{2}:){5}[0-9A-F]{2}"))throw new Exception("Dirección Bluetooth inválida");
        BluetoothManager manager=(BluetoothManager)c.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter=manager==null?null:manager.getAdapter();
        if(adapter==null||!adapter.isEnabled())throw new Exception("Activa Bluetooth");
        BluetoothDevice device=adapter.getRemoteDevice(mac);
        // RFCOMM/SPP, propio de impresoras térmicas clásicas. No cubre BLE/GATT.
        try(BluetoothSocket sock=device.createRfcommSocketToServiceRecord(SPP)) {
            sock.connect();
            OutputStream out=sock.getOutputStream();out.write(data);out.flush();
        }
    }
    private static void printUsb(Context c,String id,byte[] data) throws Exception {
        String[] parts=id.split(":");if(parts.length!=3)throw new Exception("ID USB inválido");
        int vid=Integer.parseInt(parts[1]),pid=Integer.parseInt(parts[2]);
        UsbManager manager=(UsbManager)c.getSystemService(Context.USB_SERVICE);
        if(manager==null)throw new Exception("No hay controlador USB");
        for(UsbDevice dev:manager.getDeviceList().values()){
            if(dev.getVendorId()!=vid||dev.getProductId()!=pid)continue;
            if(!manager.hasPermission(dev))throw new Exception("Autoriza esta impresora USB desde la app antes de imprimir");
            for(int i=0;i<dev.getInterfaceCount();i++){
                UsbInterface face=dev.getInterface(i);
                for(int j=0;j<face.getEndpointCount();j++){
                    UsbEndpoint ep=face.getEndpoint(j);
                    if(ep.getDirection()!=UsbConstants.USB_DIR_OUT||ep.getType()!=UsbConstants.USB_ENDPOINT_XFER_BULK)continue;
                    UsbDeviceConnection connection=manager.openDevice(dev);
                    if(connection==null)throw new Exception("No se pudo abrir USB");
                    try{
                        if(!connection.claimInterface(face,true))throw new Exception("No se pudo reservar interfaz USB");
                        int offset=0;
                        while(offset<data.length){
                            int count=Math.min(2048,data.length-offset);
                            byte[] chunk=new byte[count];System.arraycopy(data,offset,chunk,0,count);
                            int done=connection.bulkTransfer(ep,chunk,count,7000);
                            if(done<=0)throw new Exception("Fallo al transmitir por USB");
                            offset+=done;
                        }
                        return;
                    }finally{connection.releaseInterface(face);connection.close();}
                }
            }
        }
        throw new Exception("Impresora USB desconectada o sin salida de impresión BULK");
    }
    static void printTcp(String ip,int port,byte[] data)throws Exception{
        if(!isIPv4(ip)||port<1||port>65535)throw new Exception("Destino de red inválido");
        try(Socket socket=new Socket()){
            socket.connect(new InetSocketAddress(ip,port),6000);socket.setSoTimeout(7000);
            OutputStream out=socket.getOutputStream();out.write(data);out.flush();
        }
    }
}
