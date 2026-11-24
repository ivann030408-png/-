package com.example.renderer

import android.opengl.GLSurfaceView
import android.os.Build
import android.view.Surface
import android.view.SurfaceHolder
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.engine.GameEngine

@Composable
fun GameGlSurfaceView(
    engine: GameEngine,
    targetFps: Int = 120,
    onLoadStateChanged: ((SceneLoadState) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestLoadCallback = androidx.compose.runtime.rememberUpdatedState(onLoadStateChanged)

    val renderer = remember(engine) {
        ChickenGlRenderer(
            engine = engine,
            assets = context.assets,
            onLoadStateChanged = { state -> latestLoadCallback.value?.invoke(state) }
        )
    }

    val glView = remember(engine, renderer) {
        GLSurfaceView(context).apply {
            setEGLContextClientVersion(2)
            preserveEGLContextOnPause = true
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }
    }

    val currentTargetFps = androidx.compose.runtime.rememberUpdatedState(targetFps)

    // Hint Android 11+ SurfaceFlinger compositor to unlock 90Hz / 120Hz or save power at 30/45/60Hz
    LaunchedEffect(glView, targetFps) {
        applyPreferredFrameRate(glView.holder, targetFps)
    }

    DisposableEffect(lifecycleOwner, glView, renderer) {
        val holderCallback = object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                applyPreferredFrameRate(holder, currentTargetFps.value)
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                applyPreferredFrameRate(holder, currentTargetFps.value)
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) = Unit
        }
        glView.holder.addCallback(holderCallback)

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> glView.onResume()
                Lifecycle.Event.ON_PAUSE -> glView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            glView.holder.removeCallback(holderCallback)
            lifecycleOwner.lifecycle.removeObserver(observer)
            glView.onPause()
            renderer.releaseResources()
        }
    }

    AndroidView(
        factory = { glView },
        modifier = modifier.fillMaxSize()
    )
}

private fun applyPreferredFrameRate(holder: SurfaceHolder, targetFps: Int) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val surface = holder.surface
        if (surface != null && surface.isValid) {
            try {
                surface.setFrameRate(
                    targetFps.coerceIn(30, 120).toFloat(),
                    Surface.FRAME_RATE_COMPATIBILITY_DEFAULT
                )
            } catch (_: Throwable) {
            }
        }
    }
}
