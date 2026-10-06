package ai.d0548448174.qwenhebrew

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var engine: InferenceEngine
    private lateinit var status: TextView
    private lateinit var prompt: EditText
    private lateinit var output: TextView
    private lateinit var send: Button
    private lateinit var progress: ProgressBar
    private var generationJob: Job? = null
    private var modelFile: File? = null
    private var ready = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()

        lifecycleScope.launch(Dispatchers.Default) {
            try {
                engine = AiChat.getInferenceEngine(applicationContext)
                withContext(Dispatchers.Main) { status.text = "טוען את מודל Qwen..." }

                val file = ensureModel()
                modelFile = file
                engine.loadModel(file.absolutePath)
                engine.setSystemPrompt(
                    "אתה עוזר AI מקומי בעברית. ענה בעברית טבעית, ברורה וקצרה. " +
                        "אתה יכול להמציא סיפורים, לספר בדיחות, לכתוב לפי נושא, " +
                        "לשכתב, להרחיב ולקצר טקסט."
                )

                ready = true
                withContext(Dispatchers.Main) {
                    progress.visibility = View.GONE
                    send.isEnabled = true
                    status.text = "✅ מוכן — עובד אופליין"
                    output.text = "המודל מוכן. כתוב משהו למטה."
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    progress.visibility = View.GONE
                    status.text = "❌ שגיאה בטעינת המודל"
                    output.text = t.message ?: t.toString()
                    Toast.makeText(this@MainActivity, "טעינת המודל נכשלה", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private suspend fun ensureModel(): File = withContext(Dispatchers.IO) {
        val dir = File(filesDir, "models")
        if (!dir.exists()) dir.mkdirs()

        val target = File(dir, MODEL_NAME)
        if (!target.exists() || target.length() < 100_000_000L) {
            assets.open(MODEL_NAME).use { input ->
                target.outputStream().use { outputStream ->
                    input.copyTo(outputStream, 1024 * 1024)
                }
            }
        }
        target
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(Color.rgb(17, 19, 24))
        }

        val title = TextView(this).apply {
            text = "🤖 Qwen עברית אופליין"
            textSize = 25f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 14)
        }

        status = TextView(this).apply {
            text = "מתחיל..."
            textSize = 16f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, 12)
        }

        progress = ProgressBar(this).apply {
            isIndeterminate = true
        }

        output = TextView(this).apply {
            textSize = 18f
            setTextColor(Color.WHITE)
            setPadding(18, 18, 18, 18)
            setBackgroundColor(Color.rgb(29, 32, 39))
        }

        val outputScroll = ScrollView(this).apply {
            addView(output)
        }

        prompt = EditText(this).apply {
            hint = "כתוב כאן: סיפור, בדיחה, שאלה או טקסט לשכתוב..."
            textSize = 17f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setPadding(18, 8, 18, 8)
            minLines = 3
            maxLines = 6
            gravity = Gravity.TOP or Gravity.RIGHT
        }

        send = Button(this).apply {
            text = "✨ שאל את ה-AI"
            isEnabled = false
            setOnClickListener { generate() }
        }

        root.addView(title, LinearLayout.LayoutParams(-1, -2))
        root.addView(status, LinearLayout.LayoutParams(-1, -2))
        root.addView(progress, LinearLayout.LayoutParams(-1, -2))
        root.addView(outputScroll, LinearLayout.LayoutParams(-1, 0, 1f).apply {
            setMargins(0, 0, 0, 14)
        })
        root.addView(prompt, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(0, 0, 0, 10)
        })
        root.addView(send, LinearLayout.LayoutParams(-1, -2))

        setContentView(root)
    }

    private fun generate() {
        if (!ready || generationJob?.isActive == true) return
        val text = prompt.text.toString().trim()
        if (text.isEmpty()) return

        prompt.isEnabled = false
        send.isEnabled = false
        status.text = "כותב..."
        output.text = ""

        generationJob = lifecycleScope.launch(Dispatchers.Default) {
            try {
                val builder = StringBuilder()
                engine.sendUserPrompt(text, predictLength = 384).collect { token ->
                    builder.append(token)
                    withContext(Dispatchers.Main) {
                        output.text = builder.toString()
                        output.parent?.let { view ->
                            if (view is ScrollView) view.fullScroll(View.FOCUS_DOWN)
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    status.text = "✅ מוכן לתשובה הבאה"
                    prompt.isEnabled = true
                    send.isEnabled = true
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    status.text = "❌ שגיאה"
                    output.text = t.message ?: t.toString()
                    prompt.isEnabled = true
                    send.isEnabled = true
                }
            }
        }
    }

    override fun onDestroy() {
        generationJob?.cancel()
        if (::engine.isInitialized) engine.destroy()
        super.onDestroy()
    }

    companion object {
        private const val MODEL_NAME = "qwen2.5-0.5b-instruct-q4_0.gguf"
    }
}
