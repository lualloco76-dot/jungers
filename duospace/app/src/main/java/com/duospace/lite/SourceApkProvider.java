package com.duospace.lite;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.pm.ApplicationInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.database.MatrixCursor;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

/** A narrowly scoped, read-only provider for a selected package's APK files. */
public final class SourceApkProvider extends ContentProvider {
    static final String AUTHORITY = "com.duospace.lite.source";

    static ArrayList<Uri> urisFor(android.content.Context context, String packageName)
            throws android.content.pm.PackageManager.NameNotFoundException {
        ApplicationInfo info = context.getPackageManager().getApplicationInfo(packageName, 0);
        ArrayList<Uri> out = new ArrayList<>();
        out.add(new Uri.Builder().scheme("content").authority(AUTHORITY)
                .appendPath("apk").appendPath(packageName).appendPath("base").build());
        if (info.splitSourceDirs != null) {
            for (int i=0; i<info.splitSourceDirs.length; i++) {
                out.add(new Uri.Builder().scheme("content").authority(AUTHORITY)
                        .appendPath("apk").appendPath(packageName)
                        .appendPath("split"+i).build());
            }
        }
        return out;
    }

    private File fileFor(Uri uri) throws FileNotFoundException {
        List<String> seg = uri.getPathSegments();
        if (seg.size()!=3 || !"apk".equals(seg.get(0))) throw new FileNotFoundException();
        String pkg=seg.get(1), type=seg.get(2);
        try {
            ApplicationInfo info = getContext().getPackageManager().getApplicationInfo(pkg,0);
            String path;
            if ("base".equals(type)) path = info.sourceDir;
            else if (type.startsWith("split")) {
                String suffix = type.substring(5);
                int i = Integer.parseInt(suffix);
                if (i<0 || info.splitSourceDirs==null || i>=info.splitSourceDirs.length)
                    throw new FileNotFoundException();
                path=info.splitSourceDirs[i];
            } else throw new FileNotFoundException();
            if (path==null || !path.endsWith(".apk")) throw new FileNotFoundException();
            return new File(path);
        } catch (Exception e) { throw new FileNotFoundException("Invalid package or split"); }
    }

    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "application/vnd.android.package-archive"; }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Read-only");
        return ParcelFileDescriptor.open(fileFor(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Cursor query(Uri uri,String[] p,String s,String[] sa,String so) {
        try {
            File file=fileFor(uri);
            MatrixCursor c=new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE});
            c.addRow(new Object[]{file.getName(),file.length()});
            return c;
        } catch (FileNotFoundException e) { return null; }
    }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri,String selection,String[] args) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri,ContentValues values,String sel,String[] args) { throw new UnsupportedOperationException(); }
}
