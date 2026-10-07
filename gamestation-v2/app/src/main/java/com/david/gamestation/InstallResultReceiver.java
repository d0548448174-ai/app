package com.david.gamestation;

import android.content.*;
import android.app.*;
import android.content.pm.PackageInstaller;

public class InstallResultReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        int s=i.getIntExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE);
        if (s==PackageInstaller.STATUS_SUCCESS) {
            String pkg=i.getStringExtra("pkg");
            Intent l=c.getPackageManager().getLaunchIntentForPackage(pkg);
            if(l!=null){l.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);c.startActivity(l);}
        }
    }
}
