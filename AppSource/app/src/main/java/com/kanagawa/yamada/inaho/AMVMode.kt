package com.kanagawa.yamada.inaho

import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun AMVVideoSurface(
    playerService: PlayerService?,
    isBackground: Boolean = false,
    modifier: Modifier = Modifier
) {
    val playerState by PlayerService.playerState.collectAsState()
    val vw = playerState.videoWidth.toFloat()
    val vh = playerState.videoHeight.toFloat()

    AndroidView(
        factory = { context ->
            TextureView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                addOnLayoutChangeListener { v, left, top, right, bottom, _, _, _, _ ->
                    val width = right - left
                    val height = bottom - top
                    if (vw > 0 && vh > 0 && width > 0 && height > 0) {
                        val viewRatio = width.toFloat() / height.toFloat()
                        val videoRatio = vw / vh
                        val scaleX = if (videoRatio > viewRatio) videoRatio / viewRatio else 1f
                        val scaleY = if (videoRatio < viewRatio) viewRatio / videoRatio else 1f

                        val matrix = android.graphics.Matrix()
                        matrix.setScale(scaleX, scaleY, width / 2f, height / 2f)
                        (v as TextureView).setTransform(matrix)
                    }
                }

                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                        val surface = Surface(texture)
                        tag = surface
                        if (isBackground) playerService?.setBgVideoSurface(surface)
                        else playerService?.setVideoSurface(surface)
                    }

                    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {}

                    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                        if (isBackground) playerService?.setBgVideoSurface(null)
                        else playerService?.setVideoSurface(null)

                        (tag as? Surface)?.release()
                        tag = null
                        return true
                    }

                    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {}
                }
            }
        },
        update = { view ->
            // Safely push surface, checking if it is still valid to avoid passing broken surfaces to MediaPlayer
            if (playerService != null && view.isAvailable) {
                var existingSurface = view.tag as? Surface
                if (existingSurface == null || !existingSurface.isValid) {
                    existingSurface?.release()
                    existingSurface = Surface(view.surfaceTexture)
                    view.tag = existingSurface
                    if (isBackground) playerService.setBgVideoSurface(existingSurface)
                    else playerService.setVideoSurface(existingSurface)
                } else {
                    if (isBackground && playerService.currentBgSurface !== existingSurface) {
                        playerService.setBgVideoSurface(existingSurface)
                    } else if (!isBackground && playerService.currentSurface !== existingSurface) {
                        playerService.setVideoSurface(existingSurface)
                    }
                }
            }

            // Apply crop matrix on recomposition
            if (vw > 0 && vh > 0 && view.width > 0 && view.height > 0) {
                val viewRatio = view.width.toFloat() / view.height.toFloat()
                val videoRatio = vw / vh
                val scaleX = if (videoRatio > viewRatio) videoRatio / viewRatio else 1f
                val scaleY = if (videoRatio < viewRatio) viewRatio / videoRatio else 1f

                val matrix = android.graphics.Matrix()
                matrix.setScale(scaleX, scaleY, view.width / 2f, view.height / 2f)
                view.setTransform(matrix)
            }
        },
        modifier = modifier.fillMaxSize().background(Color.Transparent)
    )
}