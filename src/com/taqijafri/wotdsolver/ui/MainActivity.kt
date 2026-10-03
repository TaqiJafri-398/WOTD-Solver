package com.taqijafri.wotdsolver.ui

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.taqijafri.wotdsolver.ImageHolder
import com.taqijafri.wotdsolver.R
import com.taqijafri.wotdsolver.ScreenshotAnalyzer
import com.taqijafri.wotdsolver.data.HistoryEntry
import com.taqijafri.wotdsolver.data.HistoryStore
import com.taqijafri.wotdsolver.data.WordDatabase
import java.io.File

class MainActivity : BaseActivity() {

    private lateinit var preview: ImageView
    private lateinit var previewHint: TextView
    private var progressDialog: AlertDialog? = null
    private var cameraFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        preview = findViewById(R.id.preview)
        previewHint = findViewById(R.id.previewHint)

        findViewById<Button>(R.id.btnSelect).setOnClickListener { pickImage() }
        findViewById<Button>(R.id.btnCamera).setOnClickListener { checkCameraPermission() }
        findViewById<Button>(R.id.btnAnalyze).setOnClickListener { analyzeCurrent() }
        findViewById<Button>(R.id.btnClear).setOnClickListener {
            ImageHolder.clearImage()
            ImageHolder.analysis = null
            ImageHolder.historyId = null
            ImageHolder.historyCandidateCount = 0
            refreshPreview()
        }
        findViewById<Button>(R.id.btnManual).setOnClickListener {
            startActivity(Intent(this, ManualInputActivity::class.java))
        }
        findViewById<Button>(R.id.btnHistory).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        findViewById<Button>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<Button>(R.id.btnAbout).setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }
        refreshPreview()
    }

    override fun onResume() {
        super.onResume()
        refreshPreview()
    }

    private fun refreshPreview() {
        val bmp = ImageHolder.bitmap
        if (bmp != null && !bmp.isRecycled) {
            preview.setImageBitmap(bmp)
            preview.visibility = View.VISIBLE
            previewHint.visibility = View.GONE
        } else {
            preview.setImageBitmap(null)
            preview.visibility = View.GONE
            previewHint.visibility = View.VISIBLE
        }
    }

    // ---------------- image input ----------------

    /** Decode target: keep enough resolution; ScreenshotAnalyzer scales precisely to 1100. */
    private val DECODE_MAX_DIM = 1600

    private fun pickImage() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(
            Intent.createChooser(intent, getString(R.string.select_screenshot)),
            REQ_PICK
        )
    }

    private fun checkCameraPermission() {
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            openCamera()
        } else {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), REQ_PERM_CAMERA)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQ_PERM_CAMERA) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera()
            } else {
                toast(getString(R.string.camera_permission_needed))
            }
        }
    }

    private fun openCamera() {
        try {
            val dir = File(cacheDir, "screenshots").apply { mkdirs() }
            val f = File(dir, "capture_${System.currentTimeMillis()}.jpg")
            val uri = Uri.parse("content://com.taqijafri.wotdsolver.screenshots/${f.name}")
            cameraFile = f
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                .putExtra(MediaStore.EXTRA_OUTPUT, uri)
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val targets = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            if (targets.isEmpty()) {
                toast(getString(R.string.no_camera_app))
                return
            }
            for (ri in targets) {
                grantUriPermission(
                    ri.activityInfo.packageName, uri,
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            startActivityForResult(intent, REQ_CAMERA)
        } catch (e: Exception) {
            toast(getString(R.string.no_camera_app))
        }
    }

    @Deprecated("framework startActivityForResult is still functional")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        when (requestCode) {
            REQ_PICK -> {
                val uri = data?.data ?: return
                try {
                    val bmp = decodeUri(uri, DECODE_MAX_DIM)
                    if (bmp != null) onNewImage(bmp)
                    else toast(getString(R.string.image_load_failed))
                } catch (e: Exception) {
                    toast(getString(R.string.image_load_failed))
                }
            }
            REQ_CAMERA -> {
                val f = cameraFile
                if (f != null && f.exists()) {
                    try {
                        val bmp = decodeFile(f, DECODE_MAX_DIM)
                        if (bmp != null) onNewImage(bmp)
                        else toast(getString(R.string.image_load_failed))
                    } catch (e: Exception) {
                        toast(getString(R.string.image_load_failed))
                    }
                }
            }
        }
    }

    private fun onNewImage(bmp: Bitmap) {
        ImageHolder.clearImage()
        ImageHolder.bitmap = bmp
        ImageHolder.analysis = null
        ImageHolder.historyId = null
        ImageHolder.historyCandidateCount = 0
        refreshPreview()
        if (settings.autoAnalyze) analyzeCurrent()
    }

    private fun decodeUri(uri: Uri, maxDim: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val opts = BitmapFactory.Options().apply {
            inJustDecodeBounds = false
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxDim)
        }
        return contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }

    private fun decodeFile(f: File, maxDim: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val opts = BitmapFactory.Options().apply {
            inJustDecodeBounds = false
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxDim)
        }
        return BitmapFactory.decodeFile(f.absolutePath, opts)
    }

    private fun sampleSize(w: Int, h: Int, maxDim: Int): Int {
        var s = 1
        while (maxOf(w, h) / s > maxDim) s *= 2
        return s
    }

    // ---------------- analysis ----------------

    private fun analyzeCurrent() {
        val bmp = ImageHolder.bitmap
        if (bmp == null || bmp.isRecycled) {
            toast(getString(R.string.select_first))
            return
        }
        showProgress(getString(R.string.analyzing))
        Thread {
            try {
                val db = WordDatabase(resources, packageName)
                val result = ScreenshotAnalyzer.analyze(bmp, db)
                if (settings.historyEnabled && result.guesses.isNotEmpty()) {
                    HistoryStore(this).save(
                        HistoryEntry(
                            System.currentTimeMillis(),
                            HistoryStore.nowLabel(),
                            result.wordLength,
                            result.guesses,
                            result.candidates.size,
                            result.candidates.take(100),
                            result.bestGuessWord,
                            result.bestGuessReason
                        )
                    )
                }
                runOnUiThread {
                    hideProgress()
                    ImageHolder.analysis = result
                    ImageHolder.historyId = null
                    ImageHolder.historyCandidateCount = 0
                    startActivity(Intent(this, ResultActivity::class.java))
                }
            } catch (e: Exception) {
                runOnUiThread {
                    hideProgress()
                    toast(getString(R.string.analysis_failed))
                }
            }
        }.start()
    }

    private fun showProgress(msg: String) {
        hideProgress()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(48, 48, 48, 48)
        }
        val bar = ProgressBar(this)
        val tv = TextView(this).apply {
            text = msg
            textSize = 16f
            setPadding(32, 0, 0, 0)
        }
        row.addView(bar)
        row.addView(tv)
        progressDialog = AlertDialog.Builder(this)
            .setView(row)
            .setCancelable(false)
            .create()
        progressDialog?.show()
    }

    private fun hideProgress() {
        try {
            progressDialog?.dismiss()
        } catch (_: Exception) {
        }
        progressDialog = null
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    companion object {
        const val REQ_PICK = 1001
        const val REQ_CAMERA = 1002
        const val REQ_PERM_CAMERA = 1003
    }
}
