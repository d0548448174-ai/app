package com.example.playlist;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.*;
import android.content.*;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.database.Cursor;
import android.provider.OpenableColumns;
import android.provider.MediaStore;
import android.content.ContentValues;
import android.os.Environment;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import java.io.*;
import java.net.URLDecoder;
import java.util.*;

public class MainActivity extends Activity {
    private WebView web;
    private static final int PICK_AUDIO = 42, PICK_FOLDER = 43;
    private ValueCallback<Uri[]> pendingWebCallback;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(0xFF0A1020);
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true); s.setAllowContentAccess(true);
        s.setAllowFileAccessFromFileURLs(true); s.setAllowUniversalAccessFromFileURLs(true);
        s.setMediaPlaybackRequiresUserGesture(false);

        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                return serveImported(r.getUrl().toString());
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, String url) {
                return serveImported(url);
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                pendingWebCallback = cb; openAudioPicker(); return true;
            }
        });
        web.addJavascriptInterface(new Bridge(this), "Android");
        web.loadUrl("file:///android_asset/index.html");
        setContentView(web);
    }

    private void openAudioPicker() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"audio/*","audio/mpeg","audio/mp4","audio/wav","audio/ogg","audio/flac","audio/aac"});
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(i, PICK_AUDIO);
    }
    private void openFolderPicker() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, PICK_FOLDER);
    }

    @Override protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        if(req==PICK_AUDIO){
            Uri[] selected=collectUris(result,data);
            if(pendingWebCallback!=null){pendingWebCallback.onReceiveValue(selected);pendingWebCallback=null;}
            ArrayList<String> items=new ArrayList<>();
            if(selected!=null) for(Uri u:selected){File f=copyToImports(u);if(f!=null)items.add(jsItem(f));}
            sendItems(items); return;
        }
        if(req==PICK_FOLDER && result==RESULT_OK && data!=null && data.getData()!=null){
            Uri tree=data.getData();
            try{getContentResolver().takePersistableUriPermission(tree,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
            ArrayList<String> items=new ArrayList<>(); scanTree(tree,items,0); sendItems(items);
        }
    }
    private Uri[] collectUris(int result, Intent data){
        if(result!=RESULT_OK||data==null)return null;
        ArrayList<Uri> a=new ArrayList<>();
        if(data.getClipData()!=null)for(int i=0;i<data.getClipData().getItemCount();i++)a.add(data.getClipData().getItemAt(i).getUri());
        else if(data.getData()!=null)a.add(data.getData());
        return a.toArray(new Uri[0]);
    }
    private void scanTree(Uri tree, ArrayList<String> out, int depth){
        if(depth>6||out.size()>=200)return;
        Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));
        Cursor c=null;
        try{
            c=getContentResolver().query(children,new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE},null,null,null);
            if(c==null)return;
            while(c.moveToNext()&&out.size()<200){
                String id=c.getString(0),name=c.getString(1),mime=c.getString(2);
                Uri child=DocumentsContract.buildDocumentUriUsingTree(tree,id);
                if(DocumentsContract.Document.MIME_TYPE_DIR.equals(mime))scanTree(child,out,depth+1);
                else if(isAudio(name,mime)){File f=copyToImports(child);if(f!=null)out.add(jsItem(f));}
            }
        }catch(Exception ignored){}finally{if(c!=null)c.close();}
    }
    private boolean isAudio(String name,String mime){
        String n=name==null?"":name.toLowerCase(Locale.US);
        return (mime!=null&&mime.startsWith("audio/"))||n.endsWith(".mp3")||n.endsWith(".wav")||n.endsWith(".m4a")||n.endsWith(".aac")||n.endsWith(".ogg")||n.endsWith(".flac")||n.endsWith(".opus")||n.endsWith(".webm");
    }
    private File copyToImports(Uri uri){
        File dir=new File(getCacheDir(),"imports");if(!dir.exists())dir.mkdirs();
        String name=displayName(uri);if(name==null||name.trim().isEmpty())name="song_"+System.currentTimeMillis()+".audio";
        name=safeName(name);File out=new File(dir,System.currentTimeMillis()+"_"+name);
        try(InputStream in=getContentResolver().openInputStream(uri);OutputStream os=new BufferedOutputStream(new FileOutputStream(out))){
            if(in==null)return null;byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)os.write(b,0,n);return out;
        }catch(Exception e){return null;}
    }
    private String displayName(Uri u){
        Cursor c=null;try{c=getContentResolver().query(u,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null);if(c!=null&&c.moveToFirst())return c.getString(0);}catch(Exception ignored){}finally{if(c!=null)c.close();}return null;
    }
    private String safeName(String s){return s.replaceAll("[\\/:*?\"<>|]","_").replace("\n","_").replace("\r","_");}
    private String json(String s){return s.replace("\\","\\\\").replace(""","\\"");}
    private String jsItem(File f){
        String url="https://playlist.local/audio/"+Uri.encode(f.getName()), name=f.getName();int p=name.indexOf('_');if(p>0)name=name.substring(p+1);
        return "{\"name\":\""+json(name)+"\",\"url\":\""+json(url)+"\",\"native\":true}";
    }
    private void sendItems(ArrayList<String> items){String p="["+String.join(",",items)+"]";runOnUiThread(()->web.evaluateJavascript("window.onNativeFiles&&window.onNativeFiles("+p+")",null));}

    private WebResourceResponse serveImported(String url){
        try{
            String pre="https://playlist.local/audio/";if(!url.startsWith(pre))return null;
            String fn=URLDecoder.decode(url.substring(pre.length()),"UTF-8");
            File root=new File(getCacheDir(),"imports"), f=new File(root,fn);
            if(!f.getCanonicalPath().startsWith(root.getCanonicalPath()+File.separator)||!f.exists())return null;
            Map<String,String> h=new HashMap<>();h.put("Access-Control-Allow-Origin","*");
            return new WebResourceResponse(mimeFor(f.getName()),null,200,"OK",h,new BufferedInputStream(new FileInputStream(f)));
        }catch(Exception e){return null;}
    }
    private String mimeFor(String n){String x=n.toLowerCase(Locale.US);if(x.endsWith(".mp3"))return"audio/mpeg";if(x.endsWith(".wav"))return"audio/wav";if(x.endsWith(".m4a"))return"audio/mp4";if(x.endsWith(".aac"))return"audio/aac";if(x.endsWith(".ogg")||x.endsWith(".opus"))return"audio/ogg";if(x.endsWith(".flac"))return"audio/flac";return"application/octet-stream";}

    public class Bridge {
        final Context c; Bridge(Context x){c=x;}
        @JavascriptInterface public void openAudioPicker(){openAudioPicker();}
        @JavascriptInterface public void openFolderPicker(){openFolderPicker();}
        @JavascriptInterface public void saveBase64(String name,String mime,String data){
            new Thread(()->{try{byte[] b=Base64.decode(data,Base64.DEFAULT);
                if(Build.VERSION.SDK_INT>=29){
                    ContentValues v=new ContentValues();v.put(MediaStore.Downloads.DISPLAY_NAME,name);v.put(MediaStore.Downloads.MIME_TYPE,mime);
                    v.put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/Playlist Studio");v.put(MediaStore.Downloads.IS_PENDING,1);
                    Uri u=c.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v);if(u==null)return;
                    try(OutputStream o=c.getContentResolver().openOutputStream(u)){o.write(b);}v.clear();v.put(MediaStore.Downloads.IS_PENDING,0);c.getContentResolver().update(u,v,null,null);
                }else{File d=Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);if(!d.exists())d.mkdirs();try(FileOutputStream o=new FileOutputStream(new File(d,name))){o.write(b);}}
            }catch(Exception ignored){}}).start();
        }
    }
}