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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun AMVVideoSurface(
    playerService: PlayerService?,
    modifier: Modifier = Modifier
) {
    val playerState by PlayerService.playerState.collectAsState()

    // We get the actual video sizes to calculate the Center Crop
    val vw = playerState.videoWidth.toFloat()
    val vh = playerState.videoHeight.toFloat()

    // Assuming the parent box is 1:1, we un-squish the video mathematically
    val scaleX = if (vw > 0 && vh > 0 && vw > vh) vw / vh else 1f
    val scaleY = if (vw > 0 && vh > 0 && vh > vw) vh / vw else 1f

    AndroidView(
        factory = { context ->
            TextureView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                        val surface = Surface(texture)
                        tag = surface
                        playerService?.setVideoSurface(surface)
                    }

                    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {}

                    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                        playerService?.setVideoSurface(null)
                        (tag as? Surface)?.release()
                        tag = null
                        return true
                    }

                    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {}
                }
            }
        },
        update = { view ->
            val surface = view.tag as? Surface
            if (surface != null && playerService != null) {
                playerService.setVideoSurface(surface)
            } else if (view.isAvailable && playerService != null && view.tag == null) {
                val newSurface = Surface(view.surfaceTexture)
                view.tag = newSurface
                playerService.setVideoSurface(newSurface)
            }
        },
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            // Apply the center-crop stretch mathematically
            .graphicsLayer(
                scaleX = scaleX,
                scaleY = scaleY
            )
    )
}