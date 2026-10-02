from pathlib import Path

p = Path("build-src/app/src/main/java/com/github/sevagh/demucs_android/DemixerFragment.kt")
s = p.read_text(encoding="utf-8")

start = s.index("    private fun copyStemFilesToSelectedDirectory")
end = s.index("\n    override fun onCreateView", start)

new_func = r'''    private fun copyStemFilesToSelectedDirectory(directoryUri: Uri) {
        val context = requireContext()
        val resolver = context.contentResolver
        val directory = DocumentFile.fromTreeUri(context, directoryUri)
        if (directory == null) {
            Toast.makeText(context, "לא ניתן לפתוח את התיקייה", Toast.LENGTH_SHORT).show()
            return
        }

        val vocal = outputStems.firstOrNull { it.endsWith("/vocals.wav") }
        val playback = outputStems.firstOrNull { it.endsWith("/instrum.wav") }

        if (vocal == null || playback == null) {
            Toast.makeText(context, "התוצאות עדיין לא מוכנות", Toast.LENGTH_SHORT).show()
            return
        }

        fun copyOne(sourcePath: String, outputName: String): Boolean {
            return try {
                val source = File(sourcePath)
                val target = directory.createFile("audio/wav", outputName)
                val targetUri = target?.uri ?: return false
                resolver.openOutputStream(targetUri)?.use { output ->
                    FileInputStream(source).use { input -> input.copyTo(output) }
                }
                true
            } catch (e: Exception) {
                Log.e("VocalPlayback", "Failed to copy $outputName", e)
                false
            }
        }

        val vocalOk = copyOne(vocal, "ווקאלי.wav")
        val playbackOk = copyOne(playback, "פלייבק.wav")

        Toast.makeText(
            context,
            if (vocalOk && playbackOk) "✅ הווקאלי והפלייבק נשמרו בתיקייה שבחרת"
            else "נשמרו רק חלק מהקבצים",
            Toast.LENGTH_LONG
        ).show()
    }
'''

s = s[:start] + new_func + s[end:]
s = s.replace(
    "val btnCustomMix: Button = view.findViewById(R.id.btnCustomMix)",
    "val btnCustomMix: Button = view.findViewById(R.id.btnCustomMix)\n        btnCustomMix.visibility = View.GONE"
)
s = s.replace(
    "btnStemOutputs.setOnClickListener {",
    'btnStemOutputs.text = "⬇️ הורד ווקאלי + פלייבק"\n\n        btnStemOutputs.setOnClickListener {'
)
s = s.replace(
    'tvStemOutputs.text = "Last stems output at $currentTime"',
    'tvStemOutputs.text = "✅ מוכן להורדה · ווקאלי + פלייבק"'
)
s = s.replace(
    '"Please select an existing audio file to demix"',
    '"בחר קודם שיר מהמכשיר"'
)
p.write_text(s, encoding="utf-8")
