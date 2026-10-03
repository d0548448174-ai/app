package com.d0548448174ai.offlineshorts

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2

data class LocalVideo(val uri: Uri, val name: String)

class MainActivity : ComponentActivity() {
    private lateinit var pager: ViewPager2
    private var player: ExoPlayer? = null
    private val videos = mutableListOf<LocalVideo>()
    private var currentHolder: VideoHolder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        if (hasPermission()) showFeed() else requestPermission()
    }

    private fun hasPermission() =
        if (android.os.Build.VERSION.SDK_INT >= 33)
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
        else ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    private fun requestPermission() {
        val p = if (android.os.Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE
        ActivityCompat.requestPermissions(this, arrayOf(p), 40)
    }

    override fun onRequestPermissionsResult(r:Int,p:Array<out String>,g:IntArray) {
        super.onRequestPermissionsResult(r,p,g)
        if (r==40 && g.isNotEmpty() && g[0]==PackageManager.PERMISSION_GRANTED) showFeed()
        else showEmpty("צריך לאשר גישה לסרטונים כדי שהפיד האופליין יעבוד.")
    }

    private fun loadVideos() {
        videos.clear()
        val projection=arrayOf(MediaStore.Video.Media._ID,MediaStore.Video.Media.DISPLAY_NAME,MediaStore.Video.Media.DATE_ADDED)
        contentResolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,projection,null,null,MediaStore.Video.Media.DATE_ADDED+" DESC")?.use { c ->
            val id=c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val name=c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            while(c.moveToNext()) {
                val uri=Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,c.getLong(id).toString())
                videos.add(LocalVideo(uri,c.getString(name)))
            }
        }
    }

    private fun showFeed() {
        loadVideos()
        if (videos.isEmpty()) { showEmpty("לא נמצאו סרטונים במכשיר."); return }
        val root=FrameLayout(this).apply{setBackgroundColor(android.graphics.Color.BLACK)}
        pager=ViewPager2(this).apply{orientation=ViewPager2.ORIENTATION_VERTICAL}
        pager.adapter=FeedAdapter()
        root.addView(pager,FrameLayout.LayoutParams(-1,-1))
        addTop(root); addBottom(root)
        setContentView(root)
        pager.registerOnPageChangeCallback(object:ViewPager2.OnPageChangeCallback(){
            override fun onPageSelected(position:Int){pager.post{play(position)}}
        })
        pager.post{play(0)}
    }

    private fun play(position:Int) {
        currentHolder?.playerView?.player=null
        player?.release()
        val rv=pager.getChildAt(0) as? RecyclerView ?: return
        val holder=rv.findViewHolderForAdapterPosition(position) as? VideoHolder ?: return
        currentHolder=holder
        player=ExoPlayer.Builder(this).build().also { p ->
            holder.playerView.player=p
            p.setMediaItem(MediaItem.fromUri(videos[position].uri))
            p.repeatMode=ExoPlayer.REPEAT_MODE_ONE
            p.playWhenReady=true
            p.prepare()
        }
    }

    private fun addTop(root:FrameLayout) {
        val t=TextView(this).apply{text="For You   •   במכשיר";textSize=17f;setTextColor(-1);gravity=Gravity.CENTER}
        root.addView(t,FrameLayout.LayoutParams(-1,90,Gravity.TOP))
    }

    private fun addBottom(root:FrameLayout) {
        val b=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER;setBackgroundColor(0xDD000000.toInt())}
        listOf("⌂\nבית","⌕\nגלה","＋\nהוסף","▣\nספרייה","◉\nאני").forEach{label->
            b.addView(TextView(this).apply{text=label;textSize=12f;setTextColor(-1);gravity=Gravity.CENTER},LinearLayout.LayoutParams(0,72,1f))
        }
        root.addView(b,FrameLayout.LayoutParams(-1,72,Gravity.BOTTOM))
    }

    private fun showEmpty(message:String) {
        val l=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;setPadding(30,30,30,30);setBackgroundColor(-0x1000000)}
        l.addView(TextView(this).apply{text="▶";textSize=56f;setTextColor(-1);gravity=Gravity.CENTER})
        l.addView(TextView(this).apply{text=message;textSize=18f;setTextColor(-1);gravity=Gravity.CENTER;setPadding(0,20,0,20)})
        l.addView(Button(this).apply{text="סרוק שוב";setOnClickListener{showFeed()}})
        setContentView(l)
    }

    override fun onPause(){super.onPause();player?.pause()}
    override fun onDestroy(){player?.release();super.onDestroy()}

    inner class FeedAdapter:RecyclerView.Adapter<VideoHolder>() {
        override fun onCreateViewHolder(parent:ViewGroup,type:Int):VideoHolder {
            val f=FrameLayout(parent.context).apply{setBackgroundColor(android.graphics.Color.BLACK)}
            val pv=PlayerView(parent.context).apply{useController=false}
            f.addView(pv,FrameLayout.LayoutParams(-1,-1))
            val shade=View(parent.context).apply{setBackgroundColor(0x18000000)}
            f.addView(shade,FrameLayout.LayoutParams(-1,-1))
            val side=LinearLayout(parent.context).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;setPadding(0,0,8,130)}
            listOf("♡\nאהבתי","💬\nתגובות","↗\nשיתוף","⋮\nעוד").forEach{s->
                side.addView(TextView(parent.context).apply{text=s;textSize=14f;setTextColor(-1);gravity=Gravity.CENTER;setPadding(5,15,5,15)})
            }
            f.addView(side,FrameLayout.LayoutParams(90,-1,Gravity.END))
            val cap=TextView(parent.context).apply{textSize=16f;setTextColor(-1);setShadowLayer(8f,0f,2f,android.graphics.Color.BLACK);setPadding(18,0,100,22)}
            f.addView(cap,FrameLayout.LayoutParams(-1,100,Gravity.BOTTOM))
            return VideoHolder(f,pv,cap)
        }
        override fun onBindViewHolder(h:VideoHolder,pos:Int){h.caption.text="@offline  •  "+videos[pos].name}
        override fun getItemCount()=videos.size
    }
    inner class VideoHolder(v:View,val playerView:PlayerView,val caption:TextView):RecyclerView.ViewHolder(v)
}
