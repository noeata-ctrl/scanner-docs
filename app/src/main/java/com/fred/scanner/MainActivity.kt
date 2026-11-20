package com.fred.scanner

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.webkit.WebViewAssetLoader
import java.io.File

class MainActivity : Activity() {

    private lateinit var web: WebView
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var cameraUri: Uri? = null
    private var pendingWebPermission: PermissionRequest? = null

    companion object {
        private const val REQ_FILES = 10
        private const val REQ_CAMERA = 11
        private const val PERM_WEB = 20
        private const val PERM_INTENT = 21
        private const val HOST = "appassets.androidplatform.net"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        setContentView(web)

        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false
            allowContentAccess = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
        }

        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                loader.shouldInterceptRequest(request.url)

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (request.url.host == HOST) return false
                try { startActivity(Intent(Intent.ACTION_VIEW, request.url)) } catch (_: Exception) { }
                return true
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread {
                    if (!request.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) { request.deny(); return@runOnUiThread }
                    if (hasCamera()) request.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
                    else { pendingWebPermission?.deny(); pendingWebPermission = request; requestPermissions(arrayOf(Manifest.permission.CAMERA), PERM_WEB) }
                }
            }

            override fun onShowFileChooser(
                view: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                return if (params.isCaptureEnabled) openCamera()
                else openPicker(params.mode == FileChooserParams.MODE_OPEN_MULTIPLE)
            }
        }

        web.addJavascriptInterface(Bridge(), "AndroidBridge")

        if (savedInstanceState != null) web.restoreState(savedInstanceState)
        else web.loadUrl("https://$HOST/assets/index.html")
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    /* ---------- câmera e arquivos ---------- */

    private fun hasCamera() = checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        val ok = results.isNotEmpty() && results[0] == PackageManager.PERMISSION_GRANTED
        when (requestCode) {
            PERM_WEB -> {
                val req = pendingWebPermission; pendingWebPermission = null
                if (ok) req?.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) else req?.deny()
            }
            PERM_INTENT -> {
                if (ok) launchCameraIntent()
                else { fileCallback?.onReceiveValue(null); fileCallback = null }
            }
        }
    }

    private fun openCamera(): Boolean {
        // Como o app declara a permissão de câmera, o Android exige que ela esteja liberada até para a câmera do sistema.
        if (!hasCamera()) { requestPermissions(arrayOf(Manifest.permission.CAMERA), PERM_INTENT); return true }
        return launchCameraIntent()
    }

    private fun launchCameraIntent(): Boolean {
        return try {
            val dir = File(cacheDir, "camera").apply { mkdirs() }
            dir.listFiles()?.forEach { if (System.currentTimeMillis() - it.lastModified() > 86_400_000) it.delete() }
            val file = File(dir, "foto_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
            cameraUri = uri
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, uri)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivityForResult(intent, REQ_CAMERA)
            true
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Nenhum app de câmera encontrado", Toast.LENGTH_LONG).show()
            fileCallback?.onReceiveValue(null); fileCallback = null
            false
        } catch (e: Exception) {
            fileCallback?.onReceiveValue(null); fileCallback = null
            false
        }
    }

    private fun openPicker(multiple: Boolean): Boolean {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "application/pdf"))
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple)
        }
        return try {
            startActivityForResult(Intent.createChooser(intent, "Escolha fotos ou PDFs"), REQ_FILES)
            true
        } catch (e: Exception) {
            fileCallback?.onReceiveValue(null); fileCallback = null
            false
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val cb = fileCallback ?: return
        fileCallback = null
        if (resultCode != RESULT_OK) { cb.onReceiveValue(null); return }
        when (requestCode) {
            REQ_CAMERA -> cb.onReceiveValue(cameraUri?.let { arrayOf(it) })
            REQ_FILES -> {
                val list = mutableListOf<Uri>()
                data?.clipData?.let { c -> for (i in 0 until c.itemCount) list.add(c.getItemAt(i).uri) }
                if (list.isEmpty()) data?.data?.let { list.add(it) }
                cb.onReceiveValue(if (list.isEmpty()) null else list.toTypedArray())
            }
            else -> cb.onReceiveValue(null)
        }
    }

    /* ---------- voltar ---------- */

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        web.evaluateJavascript("(window.appBack && window.appBack()) ? 'yes' : 'no'") { r ->
            if (r?.contains("yes") != true) {
                @Suppress("DEPRECATION")
                super.onBackPressed()
            }
        }
    }

    /* ---------- ponte com a página: salvar e compartilhar ---------- */

    inner class Bridge {
        @JavascriptInterface
        fun saveToDownloads(name: String, base64: String, mime: String): String {
            return try {
                val bytes = Base64.decode(base64, Base64.DEFAULT)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, name)
                        put(MediaStore.Downloads.MIME_TYPE, mime)
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Scanner Docs")
                    }
                    val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: return "erro"
                    contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return "erro"
                    "Downloads/Scanner Docs/$name"
                } else {
                    val dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: filesDir
                    File(dir, name).writeBytes(bytes)
                    "Arquivos do app/Download/$name"
                }
            } catch (e: Exception) {
                "erro"
            }
        }

        @JavascriptInterface
        fun share(name: String, base64: String, mime: String): Boolean {
            return try {
                val bytes = Base64.decode(base64, Base64.DEFAULT)
                val dir = File(cacheDir, "compartilhar").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
                val file = File(dir, name).apply { writeBytes(bytes) }
                val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.files", file)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = mime
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, name)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runOnUiThread { startActivity(Intent.createChooser(send, "Enviar $name")) }
                true
            } catch (e: Exception) {
                false
            }
        }
    }
}
