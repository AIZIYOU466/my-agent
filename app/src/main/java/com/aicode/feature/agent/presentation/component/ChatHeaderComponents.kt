package com.aicode.feature.agent.presentation.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicode.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.aicode.core.theme.Radius
import com.aicode.core.theme.Spacing
import com.aicode.core.theme.semanticColors
import com.aicode.feature.agent.domain.model.AgentMode
import com.aicode.feature.onboarding.domain.OnboardingStep
import com.aicode.feature.onboarding.presentation.onboardingTarget
import com.aicode.feature.settings.presentation.component.ModelLogoIcon
import compose.icons.FeatherIcons
import compose.icons.feathericons.GitBranch
import compose.icons.feathericons.Globe
import compose.icons.feathericons.Menu
import compose.icons.feathericons.Plus
import compose.icons.feathericons.Terminal

@Composable
internal fun ChatHeader(
    sessionTitle: String,
    modelName: String?,
    inputTokens: Int,
    outputTokens: Int,
    onOpenDrawer: () -> Unit,
    onNewChat: () -> Unit,
    onNavigateToTerminal: () -> Unit,
    onNavigateToGit: () -> Unit,
    onNavigateToBrowser: () -> Unit = {},
    currentMode: AgentMode,
    onToggleMode: (AgentMode) -> Unit,
    connectionState: com.aicode.feature.agent.domain.container.ConnectionState? = null,
    showMenuButton: Boolean = true,
    terminalActive: Boolean = false,
    gitActive: Boolean = false,
    browserActive: Boolean = false
) {
    Surface(
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 大屏常驻侧栏时隐掉汉堡键：侧栏已经摆在左边，再给个开关反而困惑。
                if (showMenuButton) {
                    IconButton(
                        onClick = onOpenDrawer,
                        modifier = Modifier.onboardingTarget(OnboardingStep.OPEN_SIDEBAR)
                    ) {
                        Icon(
                            FeatherIcons.Menu,
                            contentDescription = stringResource(R.string.chat_open_sidebar),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Spacer(modifier = Modifier.width(Spacing.sm))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = sessionTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        if (!modelName.isNullOrBlank()) {
                            ModelLogoIcon(modelName = modelName, size = 14.dp)
                        }
                        Text(
                            text = modelName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.chat_no_model_selected),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(onClick = onNewChat) {
                    Icon(
                        FeatherIcons.Plus,
                        contentDescription = stringResource(R.string.chat_new_session),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                WorkbenchIconButton(
                    icon = FeatherIcons.GitBranch,
                    contentDescription = stringResource(R.string.chat_open_git),
                    active = gitActive,
                    onClick = onNavigateToGit
                )
                WorkbenchIconButton(
                    icon = FeatherIcons.Terminal,
                    contentDescription = stringResource(R.string.chat_open_terminal),
                    active = terminalActive,
                    onClick = onNavigateToTerminal
                )
            }
            // 远程模式：左边 SSH 连接状态，右边 token 累计统计
            if (connectionState != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ConnectionIndicator(state = connectionState)
                    TokenStats(
                        inputTokens = inputTokens,
                        outputTokens = outputTokens
                    )
                }
            }
        }
    }
}

/** 顶栏工作台入口按钮：大屏右栏开着对应内容时高亮，否则看不出点一下是开还是关。 */
@Composable
private fun WorkbenchIconButton(
    icon: ImageVector,
    contentDescription: String,
    active: Boolean,
    onClick: () -> Unit
) {
    // 背景与图标色随 active 弹簧过渡，避免硬切；参数与底部 Tab 栏一致。
    val bg by animateColorAsState(
        targetValue = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "workbench-btn-bg"
    )
    val fg by animateColorAsState(
        targetValue = if (active) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "workbench-btn-fg"
    )
    IconButton(onClick = onClick) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(Radius.sm))
                .background(bg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = fg
            )
        }
    }
}

@Composable
private fun ConnectionIndicator(
    state: com.aicode.feature.agent.domain.container.ConnectionState
) {
    val (dotColor, text) = when (state) {
        com.aicode.feature.agent.domain.container.ConnectionState.CONNECTED ->
            MaterialTheme.colorScheme.primary to stringResource(R.string.chat_ssh_connected)
        com.aicode.feature.agent.domain.container.ConnectionState.CONNECTING ->
            MaterialTheme.colorScheme.tertiary to stringResource(R.string.chat_ssh_connecting)
        com.aicode.feature.agent.domain.container.ConnectionState.FAILED ->
            MaterialTheme.colorScheme.error to stringResource(R.string.chat_ssh_failed)
        com.aicode.feature.agent.domain.container.ConnectionState.DISCONNECTED ->
            MaterialTheme.colorScheme.outline to stringResource(R.string.chat_ssh_disconnected)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TokenStats(inputTokens: Int, outputTokens: Int) {
    val inStr = formatTokenCount(inputTokens.toLong())
    val outStr = formatTokenCount(outputTokens.toLong())
    Text(
        text = "↑$inStr ↓$outStr",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
internal fun RemoteConnectingPlaceholder(
    state: com.aicode.feature.agent.domain.container.ConnectionState
) {
    val text = when (state) {
        com.aicode.feature.agent.domain.container.ConnectionState.CONNECTING -> stringResource(R.string.chat_connecting_remote)
        com.aicode.feature.agent.domain.container.ConnectionState.FAILED -> stringResource(R.string.chat_remote_connect_failed)
        com.aicode.feature.agent.domain.container.ConnectionState.DISCONNECTED -> stringResource(R.string.chat_no_remote_connection)
        com.aicode.feature.agent.domain.container.ConnectionState.CONNECTED -> ""
    }
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            if (state == com.aicode.feature.agent.domain.container.ConnectionState.CONNECTING) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun WelcomeState(
    modifier: Modifier = Modifier,
    // 预留：后续欢迎页建议点击功能接入，当前未使用
    onSuggestionClick: ((String) -> Unit)? = null
) {
    // 入场编排：三行（品牌字 → 主标题 → 提示）各持一条弹簧进度，错开 120ms 依次启动；
    // 弹簧过冲让位移轻微回弹，读作「落到位上」的重量感（呼应底部 Tab 的手感）。
    val brandEnter = remember { Animatable(0f) }
    val titleEnter = remember { Animatable(0f) }
    val hintEnter = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { brandEnter.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 300f)) }
        delay(120)
        launch { titleEnter.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 300f)) }
        delay(120)
        launch { hintEnter.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 300f)) }
    }
    // Idle 呼吸：三行不同周期、小幅错开，避免同步脉冲；与涟漪/打字点同类的「页面活着」感。
    val breath = rememberInfiniteTransition(label = "welcome-breath")
    val brandBreath by breath.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(7000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "brand-breath"
    )
    val titleBreath by breath.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "title-breath"
    )
    val hintBreath by breath.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(11000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "hint-breath"
    )
    BoxWithConstraints(
        modifier = modifier.padding(Spacing.xl),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            // 底部悬浮输入框占据大量空间，纯居中会显得偏下；整体上移 8% 屏高，让重心落在顶栏与输入框之间的空白正中
            modifier = Modifier.offset(y = -(maxHeight * 0.08f))
        ) {
            // 超大、超轻的品牌字作视觉锚点，配橙红渐变，克制不抢眼
            Text(
                text = "ya",
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.Thin,
                    letterSpacing = 6.sp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFFFA94D),
                            Color(0xFFFF5E5B)
                        )
                    )
                ),
                modifier = Modifier.graphicsLayer {
                    val t = brandEnter.value.coerceIn(0f, 1f)
                    alpha = t
                    translationY = (1f - brandEnter.value) * WELCOME_RISE_PX + (brandBreath - 0.5f) * 3f
                }
            )
            Spacer(Modifier.height(Spacing.xl))
            Text(
                text = stringResource(R.string.chat_placeholder),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.graphicsLayer {
                    val t = titleEnter.value.coerceIn(0f, 1f)
                    alpha = t
                    translationY = (1f - titleEnter.value) * WELCOME_RISE_PX + (titleBreath - 0.5f) * 2f
                }
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = stringResource(R.string.chat_input_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.semanticColors.subtleText,
                modifier = Modifier.graphicsLayer {
                    val t = hintEnter.value.coerceIn(0f, 1f)
                    alpha = t
                    translationY = (1f - hintEnter.value) * WELCOME_RISE_PX + (hintBreath - 0.5f) * 2f
                }
            )
        }
    }
}

/** 欢迎页三行的上浮位移（px）：入场起点相对静置位置。 */
private const val WELCOME_RISE_PX = 24f