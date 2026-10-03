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
    private lateinit var adapter: VideoAdapter
    private var player: ExoPlayer? = null
    private val videos = mutableListOf<LocalVideo>()
    private var currentHolder: VideoAdapter.VideoHolder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        if (hasVideoPermission()) showFeed() else requestVideoPermission()
    }

    private fun hasVideoPermission(): Boolean =
        if (android.os.Build.VERSION.SDK_INT >= 33)
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
        else ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    private fun requestVideoPermission() {
        val p = if (android.os.Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE
        ActivityCompat.requestPermissions(this, arrayOf(p), 40)
    }

    override fun onRequestPermissionsResult(requestCode:Int, permissions:Array<out String>, grantResults:IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 40 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) showFeed()
        else showEmpty("צריך לאשר גישה לסרטונים כדי להציג את הסרטונים שבמכשיר.")
    }

    private fun showFeed() {
        loadVideos()
        if (videos.isEmpty()) { showEmpty("לא מצאתי סרטונים במכשיר. הוסף סרטונים ופתח שוב."); return }
        val root = FrameLayout(this).apply { setBackgroundColor(android.graphics.Color.BLACK) }
        pager = ViewPager2(this).apply {
            orientation = ViewPager2.ORIENTATION_VERTICAL
            layoutParams = FrameLayout.LayoutParams(-1,-1)
        }
        adapter = VideoAdapter()
        pager.adapter = adapter
        root.addView(pager)
        addTopBar(root)
        addBottomBar(root)
        setContentView(root)
        pager.registerOnPageChangeCallback(object: ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position:Int) { pager.post { playPosition(position) } }
        })
        pager.post { playPosition(0) }
    }

    private fun loadVideos() {
        videos.clear()
        val projection = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.DATE_ADDED)
        contentResolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, projection, null, null, MediaStore.Video.Media.DATE_ADDED + " DESC")?.use { c ->
            val idCol=c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol=c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            while(c.moveToNext()) {
                val id=c.getLong(idCol)
                videos.add(LocalVideo(Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,id.toString()), c.getString(nameCol)))
            }
        }
    }

    private fun playPosition(position:Int) {
        currentHolder?.let { it.playerView.player=null }
        player?.release()
        if(position !in videos.indices) return
        val rv = pager.getChildAt(0) as? RecyclerView ?: return
        val vh = rv.findViewHolderForAdapterPosition(position) as? VideoAdapter.VideoHolder ?: return
        currentHolder=vh
        player=ExoPlayer.Builder(this).build().also { p ->
            vh.playerView.player=p
            p.setMediaItem(MediaItem.fromUri(videos[position].uri))
            p.repeatMode=ExoPlayer.REPEAT_MODE_ONE
            p.playWhenReady=true
            p.prepare()
        }
    }

    private fun addTopBar(root:FrameLayout) {
        val bar=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER; setPadding(18,18,18,0) }
        val title=TextView(this).apply { text="For You  •  במכשיר"; textSize=17f; setTextColor(-1); gravity=Gravity.CENTER }
        bar.addView(title,LinearLayout.LayoutParams(-1,70))
        root.addView(bar,FrameLayout.LayoutParams(-1,100,Gravity.TOP))
    }

    private fun addBottomBar(root:FrameLayout) {
        val bar=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER; setBackgroundColor(0xDD000000.toInt()) }
        listOf("⌂\nבית","⌕\nגלה","＋\nהוסף","▣\nספרייה","◉\nאני").forEach { label ->
            val t=TextView(this).apply { text=label; textSize=12f; setTextColor(-1); gravity=Gravity.CENTER; setPadding(2,10,2,8) }
            bar.addView(t,LinearLayout.LayoutParams(0,72,1f))
        }
        root.addView(bar,FrameLayout.LayoutParams(-1,76,Gravity.BOTTOM))
    }

    private fun showEmpty(message:String) {
        val l=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER; setPadding(32,32,32,32); setBackgroundColor(android.graphics.Color.BLACK) }
        val icon=TextView(this).apply { text="▶"; textSize=54f; setTextColor(-1); gravity=Gravity.CENTER }
        val msg=TextView(this).apply { text=message; textSize=17f; setTextColor(-1); gravity=Gravity.CENTER; setPadding(0,24,0,24) }
        val retry=Button(this).apply { text="סרוק שוב"; setOnClickListener { showFeed() } }
        l.addView(icon); l.addView(msg); l.addView(retry); setContentView(l)
    }

    override fun onPause(){ super.onPause(); player?.pause() }
    override fun onDestroy(){ player?.release(); super.onDestroy() }

    inner class VideoAdapter: RecyclerView.Adapter<VideoAdapter.VideoHolder>() {
        override fun onCreateViewHolder(parent:ViewGroup,viewType:Int):VideoHolder {
            val frame=FrameLayout(parent.context).apply { setBackgroundColor(android.graphics.Color.BLACK) }
            val pv=PlayerView(parent.context).apply { useController=false; layoutParams=FrameLayout.LayoutParams(-1,-1) }
            frame.addView(pv)
            val shade=View(parent.context).apply { setBackgroundColor(0x22000000); layoutParams=FrameLayout.LayoutParams(-1,-1) }
            frame.addView(shade)
            val side=LinearLayout(parent.context).apply { orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER; setPadding(0,0,12,130) }
            listOf("♡\nאהבתי","💬\nתגובות","↗\nשיתוף","⋮\nעוד").forEach { s ->
                val b=TextView(parent.context).apply { text=s; textSize=14f; setTextColor(-1); gravity=Gravity.CENTER; setPadding(6,14,6,14) }
                side.addView(b)
            }
            frame.addView(side,FrameLayout.LayoutParams(90,-1,Gravity.END))
            val caption=TextView(parent.context).apply {
                textSize=16f; setTextColor(-1); setShadowLayer(8f,0f,2f,android.graphics.Color.BLACK); setPadding(18,0,100,20)
            }
            frame.addView(caption,FrameLayout.LayoutParams(-1,110,Gravity.BOTTOM))
            return VideoHolder(frame,pv,caption)
        }
        override fun onBindViewHolder(holder:VideoHolder,position:Int){ holder.caption.text="@offline  •  " + videos[position].name }
        override fun getItemCount()=videos.size
        inner class VideoHolder(v:View,val playerView:PlayerView,val caption:TextView):RecyclerView.ViewHolder(v)
    }
}
