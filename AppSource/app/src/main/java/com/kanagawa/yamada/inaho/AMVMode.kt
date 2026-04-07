package com.kanagawa.yamada.inaho

import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun AMVVideoSurface(
    playerService: PlayerService?,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            TextureView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                        // Create and tag the surface so we can reuse it during Compose recompositions
                        val surface = Surface(texture)
                        tag = surface
                        playerService?.setVideoSurface(surface)
                    }

                    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {}

                    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                        playerService?.setVideoSurface(null)
                        (tag as? Surface)?.release()
                        tag = null
                        return true // Let Android know it can safely destroy the texture
                    }

                    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {}
                }
            }
        },
        update = { view ->
            // If the Service binds slightly after the view is created, pass the cached surface
            val surface = view.tag as? Surface
            if (surface != null && playerService != null) {
                playerService.setVideoSurface(surface)
            } else if (view.isAvailable && playerService != null && view.tag == null) {
                // Fallback catch if the listener fired before we could tag it
                val newSurface = Surface(view.surfaceTexture)
                view.tag = newSurface
                playerService.setVideoSurface(newSurface)
            }
        },
        modifier = modifier.fillMaxSize().background(Color.Black)
    )
}