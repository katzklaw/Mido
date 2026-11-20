package app.mido

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File

/**
 * Shows what went wrong after a crash. Built from plain views and run in its own process so it still works
 * when the main app can't start.
 */
class CrashActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val report = intent.getStringExtra(EXTRA_REPORT)
            ?: runCatching { File(filesDir, FILE).readText() }.getOrNull()
            ?: "No crash report found."

        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad * 2, pad, pad) }
        root.addView(TextView(this).apply { text = "Mido crashed"; textSize = 22f; setTypeface(null, Typeface.BOLD) })
        root.addView(TextView(this).apply {
            text = "Copy or share this report and send it along so the problem can be fixed."
            setPadding(0, pad / 2, 0, pad / 2)
        })
        val body = TextView(this).apply { text = report; textSize = 11f; typeface = Typeface.MONOSPACE; setTextIsSelectable(true) }
        root.addView(ScrollView(this).apply { addView(body) }, LinearLayout.LayoutParams(-1, 0, 1f))

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(Button(this).apply {
            text = "Copy"
            setOnClickListener {
                (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Mido crash", report))
                Toast.makeText(this@CrashActivity, "Copied", Toast.LENGTH_SHORT).show()
            }
        })
        buttons.addView(Button(this).apply {
            text = "Share"
            setOnClickListener {
                startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report),
                        "Share crash report",
                    ),
                )
            }
        })
        buttons.addView(Button(this).apply { text = "Close"; setOnClickListener { finishAffinity() } })
        root.addView(buttons)
        setContentView(root)
    }

    companion object {
        const val EXTRA_REPORT = "report"
        const val FILE = "last_crash.txt"
    }
}
