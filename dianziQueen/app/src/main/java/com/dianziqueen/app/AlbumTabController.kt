package com.dianziqueen.app

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.util.concurrent.Executors

/**
 * 激活后「相册」分页：展示本 App 加密入库的照片，右上角添加（相册选取 / 拍摄）。
 */
class AlbumTabController(
    private val activity: AppCompatActivity,
    albumRoot: View,
    private val onPointsChanged: () -> Unit = {},
) {
    companion object {
        private const val COST_VIEW = 5
        private const val COST_REDEEM = 20
        private const val COST_DELETE = 40
        private const val VIEW_MAX_SIDE = 2048
    }
    private val albumSubtitleText: TextView = albumRoot.findViewById(R.id.albumSubtitleText)
    private val albumEmptyText: TextView = albumRoot.findViewById(R.id.albumEmptyText)
    private val albumGrid: RecyclerView = albumRoot.findViewById(R.id.albumGrid)
    private val albumAddButton: ImageButton = albumRoot.findViewById(R.id.albumAddButton)

    private val adapter = AlbumPhotoAdapter(
        onPhotoClick = { id -> confirmAndViewPhoto(id) },
        onPhotoLongClick = { id -> showPhotoActionsDialog(id) },
    )
    private val decodeExecutor = Executors.newSingleThreadExecutor()
    private val thumbCache = android.util.LruCache<String, Bitmap>(24)

    private var pendingCaptureUri: Uri? = null

    private val pickImageLauncher = activity.registerForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        importFromUri(uri)
    }

    private val takePictureLauncher = activity.registerForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success ->
        val uri = pendingCaptureUri
        pendingCaptureUri = null
        if (success && uri != null) {
            importFromUri(uri)
            try {
                activity.contentResolver.delete(uri, null, null)
            } catch (_: Exception) { }
        }
    }

    private val cameraPermissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) launchCamera() else {
            Toast.makeText(activity, R.string.album_camera_denied, Toast.LENGTH_SHORT).show()
        }
    }

    init {
        albumGrid.layoutManager = GridLayoutManager(activity, 3)
        albumGrid.adapter = adapter
        adapter.setBitmapLoader { id -> thumbCache.get(id) ?: loadThumbnail(id) }

        albumAddButton.setOnClickListener { showAddPhotoDialog() }
    }

    /** 上交权限按钮点击时预生成密钥。 */
    fun onSubmitClicked() {
        QueenAlbumVault.ensureMasterKey(activity)
    }

    /** 进入相册分页：检查密钥并刷新列表。 */
    fun onTabShown() {
        if (!QueenAlbumVault.ensureMasterKey(activity)) {
            Toast.makeText(activity, R.string.album_key_failed, Toast.LENGTH_SHORT).show()
        }
        refreshGrid()
    }

    fun refreshGrid() {
        val ids = QueenAlbumVault.listPhotoIds(activity)
        albumSubtitleText.text =
            activity.getString(R.string.album_subtitle_fmt, ids.size)
        adapter.submitList(ids)
        albumEmptyText.visibility = if (ids.isEmpty()) View.VISIBLE else View.GONE
        albumGrid.visibility = if (ids.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun showAddPhotoDialog() {
        val labels = mutableListOf(activity.getString(R.string.album_add_pick))
        val actions = mutableListOf<() -> Unit>({ pickImageLauncher.launch("image/*") })
        if (isCameraCaptureAvailable()) {
            labels.add(activity.getString(R.string.album_add_camera))
            actions.add { requestCameraAndCapture() }
        }
        AlertDialog.Builder(activity)
            .setTitle(R.string.album_add_title)
            .setItems(labels.toTypedArray()) { _, which ->
                actions.getOrNull(which)?.invoke()
            }
            .show()
    }

    private fun isCameraCaptureAvailable(): Boolean {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        return intent.resolveActivity(activity.packageManager) != null
    }

    private fun buildCaptureIntent(outputUri: Uri): Intent {
        return Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, outputUri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newUri(
                activity.contentResolver,
                "capture",
                outputUri,
            )
        }
    }

    private fun canHandleCaptureIntent(outputUri: Uri): Boolean {
        return buildCaptureIntent(outputUri).resolveActivity(activity.packageManager) != null
    }

    private fun requestCameraAndCapture() {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            launchCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchCamera() {
        val captureFile = File(activity.cacheDir, "queen_capture_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            activity,
            "${activity.packageName}.fileprovider",
            captureFile,
        )
        if (!canHandleCaptureIntent(uri)) {
            Toast.makeText(activity, R.string.album_camera_unavailable, Toast.LENGTH_LONG).show()
            return
        }
        pendingCaptureUri = uri
        try {
            takePictureLauncher.launch(uri)
        } catch (_: ActivityNotFoundException) {
            pendingCaptureUri = null
            Toast.makeText(activity, R.string.album_camera_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    private fun importFromUri(uri: Uri) {
        val bytes = try {
            activity.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (_: Exception) {
            null
        }
        if (bytes == null || bytes.isEmpty()) {
            Toast.makeText(activity, R.string.album_import_failed, Toast.LENGTH_SHORT).show()
            return
        }
        val id = QueenAlbumVault.importPlainBytes(activity, bytes)
        if (id == null) {
            Toast.makeText(activity, R.string.album_import_failed, Toast.LENGTH_SHORT).show()
            return
        }
        preloadThumbnail(id)
        refreshGrid()
        Toast.makeText(activity, R.string.album_import_ok, Toast.LENGTH_SHORT).show()
    }

    private fun loadThumbnail(id: String): Bitmap? {
        val cacheKey = thumbnailCacheKey(id)
        val cached = thumbCache.get(cacheKey)
        if (cached != null) return cached
        val plain = QueenAlbumVault.decryptToBytes(activity, id) ?: return null
        val decoded = AlbumPhotoAdapter.decodeThumbnail(plain) ?: return null
        val redeemed = QueenAlbumVault.isRedeemed(activity, id)
        val bmp = if (redeemed) {
            decoded
        } else {
            val blurred = AlbumBlurHelper.blurForAlbumThumbnail(decoded)
            if (blurred !== decoded) decoded.recycle()
            blurred
        }
        thumbCache.put(cacheKey, bmp)
        return bmp
    }

    private fun thumbnailCacheKey(id: String): String {
        return if (QueenAlbumVault.isRedeemed(activity, id)) "$id:r" else "$id:l"
    }

    private fun preloadThumbnail(id: String) {
        decodeExecutor.execute {
            loadThumbnail(id)
            activity.runOnUiThread {
                val pos = adapter.currentList.indexOf(id)
                if (pos >= 0) adapter.notifyItemChanged(pos)
            }
        }
    }

    fun shutdown() {
        decodeExecutor.shutdownNow()
        thumbCache.evictAll()
    }

    private fun currentPoints(): Int = QueenPointsStore.getPoints(activity)

    private fun toastInsufficient(cost: Int) {
        Toast.makeText(
            activity,
            activity.getString(R.string.album_points_insufficient, cost, currentPoints()),
            Toast.LENGTH_LONG,
        ).show()
    }

    private fun spendOrToast(cost: Int): Boolean {
        if (QueenPointsStore.trySpend(activity, cost)) {
            onPointsChanged()
            return true
        }
        toastInsufficient(cost)
        return false
    }

    private fun confirmAndViewPhoto(photoId: String) {
        if (QueenAlbumVault.isRedeemed(activity, photoId)) {
            openPhotoViewer(photoId)
            return
        }
        val points = currentPoints()
        AlertDialog.Builder(activity)
            .setMessage(activity.getString(R.string.album_view_confirm, COST_VIEW, points))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                if (!spendOrToast(COST_VIEW)) return@setPositiveButton
                openPhotoViewer(photoId)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun openPhotoViewer(photoId: String) {
        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_album_viewer, null)
        val imageView = dialogView.findViewById<ImageView>(R.id.albumViewerImage)
        val progress = dialogView.findViewById<ProgressBar>(R.id.albumViewerProgress)
        imageView.visibility = View.GONE

        val dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.album_viewer_title)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok, null)
            .create()

        decodeExecutor.execute {
            val plain = QueenAlbumVault.decryptToBytes(activity, photoId)
            val bitmap = plain?.let { decodeForViewer(it) }
            activity.runOnUiThread {
                progress.visibility = View.GONE
                if (bitmap == null) {
                    QueenPointsStore.addPoints(activity, COST_VIEW)
                    onPointsChanged()
                    dialog.dismiss()
                    Toast.makeText(activity, R.string.album_view_decode_failed, Toast.LENGTH_SHORT)
                        .show()
                    return@runOnUiThread
                }
                imageView.visibility = View.VISIBLE
                imageView.setImageBitmap(bitmap)
                dialog.show()
            }
        }
    }

    private fun decodeForViewer(bytes: ByteArray): android.graphics.Bitmap? {
        return AlbumPhotoAdapter.decodeThumbnail(bytes, VIEW_MAX_SIDE)
    }

    private fun showPhotoActionsDialog(photoId: String) {
        val points = currentPoints()
        val redeemed = QueenAlbumVault.isRedeemed(activity, photoId)
        val labels = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()
        if (!redeemed) {
            labels.add(activity.getString(R.string.album_action_redeem_fmt, COST_REDEEM))
            actions.add { confirmRedeem(photoId, points) }
        } else {
            labels.add(activity.getString(R.string.album_action_delete_fmt, COST_DELETE))
            actions.add { confirmDelete(photoId, points) }
        }
        AlertDialog.Builder(activity)
            .setTitle(R.string.album_action_title)
            .setItems(labels.toTypedArray()) { _, which ->
                actions.getOrNull(which)?.invoke()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmRedeem(photoId: String, points: Int) {
        AlertDialog.Builder(activity)
            .setMessage(
                activity.getString(R.string.album_redeem_confirm, COST_REDEEM, points),
            )
            .setPositiveButton(android.R.string.ok) { _, _ -> performRedeem(photoId) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmDelete(photoId: String, points: Int) {
        if (!QueenAlbumVault.isRedeemed(activity, photoId)) {
            Toast.makeText(activity, R.string.album_delete_need_redeem, Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(activity)
            .setMessage(
                activity.getString(R.string.album_delete_confirm, COST_DELETE, points),
            )
            .setPositiveButton(android.R.string.ok) { _, _ -> performDelete(photoId) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun performRedeem(photoId: String) {
        if (QueenAlbumVault.isRedeemed(activity, photoId)) {
            Toast.makeText(activity, R.string.album_already_redeemed, Toast.LENGTH_SHORT).show()
            return
        }
        if (!spendOrToast(COST_REDEEM)) return
        if (QueenAlbumVault.redeemPhoto(activity, photoId)) {
            thumbCache.remove("$photoId:l")
            thumbCache.remove("$photoId:r")
            preloadThumbnail(photoId)
            refreshGrid()
            Toast.makeText(activity, R.string.album_redeem_ok, Toast.LENGTH_SHORT).show()
        } else {
            QueenPointsStore.addPoints(activity, COST_REDEEM)
            onPointsChanged()
            Toast.makeText(activity, R.string.album_redeem_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun performDelete(photoId: String) {
        if (!QueenAlbumVault.isRedeemed(activity, photoId)) {
            Toast.makeText(activity, R.string.album_delete_need_redeem, Toast.LENGTH_LONG).show()
            return
        }
        if (!spendOrToast(COST_DELETE)) return
        val ok = QueenAlbumVault.deletePhoto(activity, photoId)
        if (ok) {
            thumbCache.remove("$photoId:l")
            thumbCache.remove("$photoId:r")
            refreshGrid()
            Toast.makeText(activity, R.string.album_delete_ok, Toast.LENGTH_SHORT).show()
        } else {
            QueenPointsStore.addPoints(activity, COST_DELETE)
            onPointsChanged()
            Toast.makeText(activity, R.string.album_delete_failed, Toast.LENGTH_SHORT).show()
        }
    }
}
