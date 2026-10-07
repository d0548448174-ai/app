package com.david.gamestation;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.Settings;
import java.io.*;
import java.util.*;

public class GameStore {
    private final MainActivity ctx;
    private final File dir;
    private final android.content.SharedPreferences prefs;

    public static class Item {
        public String id, name, pkg, apk, icon;
    }

    public GameStore(MainActivity c) {
        ctx = c;
        dir = new File(c.getFilesDir(), "game_library");
        if (!dir.exists()) dir.mkdirs();
        prefs = c.getSharedPreferences("game_library", Context.MODE_PRIVATE);
    }

    public ArrayList<Item> list() {
        ArrayList<Item> out = new ArrayList<>();
        Set<String> ids = prefs.getStringSet("ids", new HashSet<String>());
        for (String id : ids) {
            String raw = prefs.getString("item_" + id, null);
            if (raw == null) continue;
            String[] x = raw.split("\\|", 5);
            if (x.length < 4) continue;
            Item g = new Item();
            g.id=x[0]; g.pkg=x[1]; g.name=x[2]; g.apk=x[3]; g.icon=x.length==5?x[4]:"";
            if (new File(g.apk).exists()) out.add(g);
        }
        return out;
    }

    public void importApk(Uri uri, final GameView view) {
        try {
            String id = "game_" + System.currentTimeMillis();
            File apk = new File(dir, id + ".apk");
            InputStream in = ctx.getContentResolver().openInputStream(uri);
            if (in == null) throw new IOException("open");
            FileOutputStream out = new FileOutputStream(apk);
            byte[] buf = new byte[65536];
            int n;
            while ((n=in.read(buf)) > 0) out.write(buf,0,n);
            in.close(); out.close();

            PackageInfo info = ctx.getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(), 0);
            if (info == null || info.applicationInfo == null) throw new IOException("invalid apk");
            info.applicationInfo.sourceDir = apk.getAbsolutePath();
            info.applicationInfo.publicSourceDir = apk.getAbsolutePath();
            String pkg = info.packageName;
            String name = info.applicationInfo.loadLabel(ctx.getPackageManager()).toString();
            String icon = saveIcon(id, info.applicationInfo.loadIcon(ctx.getPackageManager()));

            HashSet<String> ids = new HashSet<>(prefs.getStringSet("ids", new HashSet<String>()));
            ids.add(id);
            prefs.edit()
                    .putStringSet("ids", ids)
                    .putString("item_"+id, id+"|"+pkg+"|"+name+"|"+apk.getAbsolutePath()+"|"+icon)
                    .apply();

            view.refreshLibrary();
            ctx.toast("נוסף לספרייה: " + name);
        } catch (Exception e) {
            ctx.toast("לא הצלחתי לקרוא את קובץ ה‑APK");
        }
    }

    private String saveIcon(String id, Drawable drawable) throws Exception {
        File f = new File(dir, id + ".png");
        Bitmap b = Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        drawable.setBounds(0,0,64,64);
        drawable.draw(c);
        FileOutputStream out = new FileOutputStream(f);
        b.compress(Bitmap.CompressFormat.PNG, 92, out);
        out.close();
        return f.getAbsolutePath();
    }

    public void remove(Item g, GameView view) {
        try {
            new File(g.apk).delete();
            if (g.icon != null) new File(g.icon).delete();
            HashSet<String> ids = new HashSet<>(prefs.getStringSet("ids", new HashSet<String>()));
            ids.remove(g.id);
            prefs.edit().putStringSet("ids", ids).remove("item_"+g.id).apply();
            view.refreshLibrary();
            ctx.toast("המשחק הוסר מהספרייה");
        } catch (Exception ignored) {}
    }

    public void play(Item g) {
        try {
            Intent launch = ctx.getPackageManager().getLaunchIntentForPackage(g.pkg);
            if (launch != null) {
                ctx.startActivity(launch);
                return;
            }
            if (android.os.Build.VERSION.SDK_INT >= 26 && !ctx.getPackageManager().canRequestPackageInstalls()) {
                ctx.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:"+ctx.getPackageName())));
                ctx.toast("אשר התקנת אפליקציות ממקור זה, ואז לחץ PLAY שוב");
                return;
            }
            PackageInstaller.SessionParams params =
                    new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            params.setAppPackageName(g.pkg);
            PackageInstaller installer = ctx.getPackageManager().getPackageInstaller();
            int sid = installer.createSession(params);
            PackageInstaller.Session session = installer.openSession(sid);
            File f = new File(g.apk);
            FileInputStream in = new FileInputStream(f);
            OutputStream out = session.openWrite("base.apk",0,f.length());
            byte[] buf = new byte[65536];
            int n;
            while ((n=in.read(buf)) > 0) out.write(buf,0,n);
            in.close();
            session.fsync(out);
            out.close();
            Intent done = new Intent(ctx, InstallResultReceiver.class)
                    .putExtra("pkg", g.pkg);
            PendingIntent pi = PendingIntent.getBroadcast(ctx,sid,done,
                    PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            session.commit(pi.getIntentSender());
            session.close();
            ctx.toast("התקנת המשחק הופעלה");
        } catch (Exception e) {
            ctx.toast("לא ניתן להפעיל את ה‑APK");
        }
    }
}
