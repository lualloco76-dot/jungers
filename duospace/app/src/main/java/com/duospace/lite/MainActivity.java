package com.duospace.lite;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class MainActivity extends Activity {
    private LinearLayout content;
    private ArrayList<AppEntry> all = new ArrayList<>();
    private boolean work;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        DevicePolicyManager dpm = getSystemService(DevicePolicyManager.class);
        work = dpm != null && dpm.isProfileOwnerApp(getPackageName());
        if (work) ProfileAdminReceiver.finishSetup(this);
        buildView();
    }
    @Override protected void onResume() { super.onResume(); if(content!=null) renderApps(""); }

    private void buildView() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true); scroll.setBackgroundColor(Design.BACK);
        LinearLayout page = Design.column(this,20);
        scroll.addView(page);
        setContentView(scroll);
        TextView mark = Design.copy(this,"DUOSPACE  /  LITE",12);
        mark.setLetterSpacing(0.22f);mark.setTextColor(Design.ACCENT);
        page.addView(mark,Design.lp(this,-1,-2,10));
        page.addView(Design.title(this, work?"Mis apps independientes":"Clona sin ralentizar tu móvil",26),Design.lp(this,-1,-2,12));
        page.addView(Design.copy(this, work?
                "Este perfil guarda sus propios datos, incluso después de reiniciar.":
                "Un perfil de Android. Dos espacios independientes. Sin máquinas virtuales ni suscripciones.",14),
                Design.lp(this,-1,-2,9));

        LinearLayout panel = Design.column(this,16);
        panel.setBackground(Design.shape(Design.SURFACE, Design.dp(this,18)));
        page.addView(panel,Design.lp(this,-1,-2,20));
        panel.addView(Design.title(this,work?"ESPACIO DUPLICADO":"ESPACIO PRIVADO",13));
        panel.addView(Design.copy(this,work?"Instalado por Android · Datos separados · Persistente":
                "La duplicación utiliza el perfil de trabajo del sistema Android.",13),Design.lp(this,-1,-2,7));
        if (!work) {
            Button setup=Design.button(this,"1 · Crear perfil independiente");
            panel.addView(setup,Design.lp(this,-1,48,14));
            setup.setOnClickListener(v -> provision());
            Button show=Design.button(this,"2 · Abrir espacio duplicado");
            panel.addView(show,Design.lp(this,-1,48,10));
            show.setBackground(Design.shape(0xFF354367,Design.dp(this,14)));
            show.setOnClickListener(v -> openSpace());
        } else {
            panel.addView(Design.copy(this,"Importante: si eliminas el perfil de trabajo, también eliminas sus cuentas y datos.",12),Design.lp(this,-1,-2,10));
        }
        page.addView(Design.title(this,work?"Aplicaciones del perfil":"Selecciona una app para duplicar",19),Design.lp(this,-1,-2,24));
        EditText filter=new EditText(this);
        filter.setSingleLine(true);filter.setTextColor(Design.TEXT);filter.setHintTextColor(Design.MUTED);
        filter.setTextSize(14);filter.setHint("Buscar aplicación...");
        filter.setPadding(Design.dp(this,15),0,Design.dp(this,15),0);
        filter.setBackground(Design.shape(Design.SURFACE,Design.dp(this,14)));
        page.addView(filter,Design.lp(this,-1,48,13));
        content=Design.column(this,0); page.addView(content,Design.lp(this,-1,-2,10));
        filter.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count) { renderApps(s.toString()); }
            @Override public void afterTextChanged(android.text.Editable e) {}
        });
        loadApps(); renderApps("");
        page.addView(Design.copy(this,"Sin servicios residentes. Sin publicidad. Android administra el perfil de forma nativa.",12),Design.lp(this,-1,-2,17));
    }
    static final class AppEntry {
        final String pkg,label;final Drawable icon;
        AppEntry(String pkg,String label,Drawable icon) {this.pkg=pkg;this.label=label;this.icon=icon;}
    }
    private void loadApps(){
        all.clear();
        Intent launcher=new Intent(Intent.ACTION_MAIN);launcher.addCategory(Intent.CATEGORY_LAUNCHER);
        PackageManager pm=getPackageManager();
        List<ResolveInfo> apps=pm.queryIntentActivities(launcher,0);
        Set<String> seen = new HashSet<>();
        for(ResolveInfo info: apps){
            String pkg=info.activityInfo.packageName;
            if(pkg.equals(getPackageName()) || !seen.add(pkg)) continue;
            CharSequence label=info.loadLabel(pm);
            all.add(new AppEntry(pkg,label==null?pkg:label.toString(),info.loadIcon(pm)));
        }
        Collections.sort(all,Comparator.comparing(e->e.label.toLowerCase(Locale.ROOT)));
    }
    private void renderApps(String q){
        if(content==null) return;
        content.removeAllViews();
        String query=q.toLowerCase(Locale.ROOT).trim();
        int count=0;
        for(AppEntry app:all){
            if(!app.label.toLowerCase(Locale.ROOT).contains(query) && !app.pkg.contains(query)) continue;
            count++;
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Design.dp(this,12),Design.dp(this,9),Design.dp(this,12),Design.dp(this,9));
            row.setBackground(Design.shape(Design.SURFACE,Design.dp(this,14)));
            ImageView icon=new ImageView(this);icon.setImageDrawable(app.icon);
            row.addView(icon,new LinearLayout.LayoutParams(Design.dp(this,38),Design.dp(this,38)));
            LinearLayout texts=Design.column(this,0);
            LinearLayout.LayoutParams tw=new LinearLayout.LayoutParams(0,-2,1);tw.leftMargin=Design.dp(this,12);
            row.addView(texts,tw);
            TextView name=Design.title(this,app.label,14);name.setMaxLines(1);texts.addView(name);
            TextView pkg=Design.copy(this,app.pkg,11);pkg.setMaxLines(1);texts.addView(pkg);
            TextView action=Design.title(this,work?"ABRIR":"CLONAR",11);
            action.setTextColor(Design.ACCENT);row.addView(action);
            content.addView(row,Design.lp(this,-1,58,8));
            row.setOnClickListener(v->{if(work) manageWorkApp(app);else cloneApp(app);});
        }
        if(count==0) content.addView(Design.copy(this,"No hay aplicaciones que coincidan con la búsqueda.",14),Design.lp(this,-1,-2,12));
    }
    private void provision() {
        DevicePolicyManager dpm=getSystemService(DevicePolicyManager.class);
        if(dpm==null || !getPackageManager().hasSystemFeature(PackageManager.FEATURE_MANAGED_USERS)) {
            message("Este teléfono no admite perfiles de trabajo.");return;
        }
        if(!dpm.isProvisioningAllowed(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE)) {
            message("Android no permite crear otro perfil: quizá ya existe uno de trabajo o tu fabricante lo bloquea.");return;
        }
        new AlertDialog.Builder(this).setTitle("Crear espacio independiente")
                .setMessage("Android creará un perfil de trabajo separado. No borrará tus apps personales. Si eliminas ese perfil, perderás los datos de las copias.")
                .setNegativeButton("Cancelar",null)
                .setPositiveButton("Continuar",(d,w)->{
                    Intent i=new Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE);
                    i.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME,
                            ProfileAdminReceiver.admin(this));
                    if(Build.VERSION.SDK_INT>=33) i.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_ALLOW_OFFLINE,true);
                    try {startActivity(i);} catch (RuntimeException e){message("Android rechazó la configuración: "+e.getMessage());}
                }).show();
    }
    private void cloneApp(AppEntry app) {
        try {
            ArrayList<Uri> files=SourceApkProvider.urisFor(this,app.pkg);
            Intent send=new Intent(BridgeActivity.ACTION_CLONE);
            send.putExtra(BridgeActivity.EXTRA_PACKAGE,app.pkg);
            send.putExtra(BridgeActivity.EXTRA_LABEL,app.label);
            ClipData clips=new ClipData("APK",new String[]{"application/vnd.android.package-archive"},new ClipData.Item(files.get(0)));
            for(int n=1;n<files.size();n++)clips.addItem(new ClipData.Item(files.get(n)));
            send.setClipData(clips);
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(send,"Instalar en perfil de trabajo"));
        } catch (Exception e) { message("No fue posible iniciar el duplicado: "+e.getMessage()); }
    }
    private void openSpace() {
        try {
            Intent i=new Intent(BridgeActivity.ACTION_OPEN_SPACE);
            i.addCategory(Intent.CATEGORY_DEFAULT);
            startActivity(Intent.createChooser(i,"Abrir perfil de trabajo"));
        } catch(RuntimeException e){message("Primero crea el perfil de trabajo.");}
    }
    private void manageWorkApp(AppEntry app) {
        new AlertDialog.Builder(this).setTitle(app.label)
                .setItems(new String[]{"Abrir copia","Desinstalar copia (borra sus datos)"},(di,choice)->{
                    if(choice==0){
                        Intent i=getPackageManager().getLaunchIntentForPackage(app.pkg);
                        if(i==null){message("No hay acceso directo para esta aplicación.");return;}
                        try{startActivity(i);}catch(RuntimeException e){message("No se pudo abrir: "+e.getMessage());}
                    } else {
                        Intent uninstall=new Intent(Intent.ACTION_DELETE,Uri.parse("package:"+app.pkg));
                        try{startActivity(uninstall);}catch(RuntimeException e){message("No se pudo desinstalar.");}
                    }
                }).show();
    }
    private void message(String s){new AlertDialog.Builder(this).setMessage(s).setPositiveButton("Aceptar",null).show();}
}
