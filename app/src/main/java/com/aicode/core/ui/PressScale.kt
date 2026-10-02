package com.aicode.core.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * 统一按压反馈：点住时轻微缩小、松开弹簧回弹，与底部 Tab 栏 tab-press-scale 同参数。
 *
 * 这是 components「active」态的全局补强——项目里大量 `.clickable()` 没有按压反馈。
 * 与 clickable 共用同一个 [interactionSource]，确保按下状态与点击命中同步：
 *
 * ```
 * val src = remember { MutableInteractionSource() }
 * Row(Modifier.pressScale(src).clickable(interactionSource = src, onClick = { ... }))
 * ```
 *
 * [enabled] 为 false 时原样返回，便于「禁用行无反馈」沿用调用方已有的 disabled 处理。
 */
@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.94f,
    enabled: Boolean = true
): Modifier {
    if (!enabled) return this
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "press-scale"
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}