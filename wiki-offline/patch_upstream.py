#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1]).resolve()

gradle = root / "app/build.gradle.kts"
s = gradle.read_text(encoding="utf-8")
replacements = {
    'resValue("string", "app_name", "Kiwix")': 'resValue("string", "app_name", "ויקיפדיה אופליין")',
    'resValue("string", "app_search_string", "Search Kiwix")': 'resValue("string", "app_search_string", "חיפוש ויקיפדיה אופליין")',
}
for old, new in replacements.items():
    if old not in s:
        raise SystemExit(f"Missing expected Gradle setting: {old}")
    s = s.replace(old, new, 1)
if 'noCompress += "zim"' not in s:
    marker = "android {\n"
    if marker not in s:
        raise SystemExit("Could not find android block")
    s = s.replace(marker, marker + '  androidResources {\n    noCompress += "zim"\n  }\n', 1)
gradle.write_text(s, encoding="utf-8")

app = root / "app/src/main/java/org/kiwix/kiwixmobile/KiwixApp.kt"
app.write_text('''package org.kiwix.kiwixmobile

import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import org.kiwix.kiwixmobile.core.CoreApp
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

@HiltAndroidApp
class KiwixApp : CoreApp() {
  override fun onCreate() {
    super.onCreate()
    Thread({ installBundledZim() }, "wiki-offline-zim-installer").apply { isDaemon = true; start() }
  }
  private fun installBundledZim() {
    val name = "wikipedia_he_all_mini_2026-07a.zim"
    runCatching {
      val expected = assets.openFd(name).use { it.length }
      val dir = externalMediaDirs.firstOrNull() ?: filesDir
      if (!dir.exists() && !dir.mkdirs()) throw IOException("Cannot create $dir")
      val target = File(dir, name)
      if (target.isFile && target.length() == expected) return
      val temp = File(dir, "$name.part")
      assets.open(name).use { input -> FileOutputStream(temp).use { output -> input.copyTo(output, 1024 * 1024); output.fd.sync() } }
      if (temp.length() != expected) throw IOException("Incomplete ZIM copy: ${temp.length()} / $expected")
      if (target.exists() && !target.delete()) throw IOException("Cannot replace $target")
      if (!temp.renameTo(target)) throw IOException("Cannot finalize $target")
      Log.i("WikiOffline", "Hebrew ZIM installed: $target")
    }.onFailure { Log.e("WikiOffline", "Could not install bundled ZIM", it) }
  }
}
''', encoding="utf-8")

webview = root / "core/src/main/java/org/kiwix/kiwixmobile/core/main/KiwixWebView.kt"
s = webview.read_text(encoding="utf-8")
needle = '      userAgentString = "${userAgent(context)} ${getCurrentLocale(context)}"\n'
if needle not in s:
    raise SystemExit("Could not find WebView setup")
if "loadsImagesAutomatically = false" not in s:
    s = s.replace(needle, needle + "      loadsImagesAutomatically = false\n", 1)
webview.write_text(s, encoding="utf-8")

client = root / "core/src/main/java/org/kiwix/kiwixmobile/core/main/CoreWebViewClient.kt"
s = client.read_text(encoding="utf-8")
needle = "    jumpToAnchor(view, url)\n"
js = '''    view.evaluateJavascript(
      "document.querySelectorAll('img,picture,video,source').forEach(function(el){el.remove();});" +
        "document.querySelectorAll('*').forEach(function(el){el.style.backgroundImage='none';});",
      null
    )
'''
if needle not in s:
    raise SystemExit("Could not find article load hook")
if "document.querySelectorAll('img,picture,video,source')" not in s:
    s = s.replace(needle, js + needle, 1)
client.write_text(s, encoding="utf-8")
print("Patched Kiwix Android at", root)
