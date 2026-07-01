package io.base14.scout.android.instrumentation

import android.view.View
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull

internal data class ComposeTapTarget(val label: String, val role: String)

internal object ComposeTapResolver {
    fun resolve(
        composeView: View,
        screenX: Int,
        screenY: Int,
    ): ComposeTapTarget? {
        val owner = semanticsOwner(composeView) ?: return null
        val loc = IntArray(2)
        composeView.getLocationOnScreen(loc)
        val lx = (screenX - loc[0]).toFloat()
        val ly = (screenY - loc[1]).toFloat()

        val root = runCatching { owner.rootSemanticsNode }.getOrNull() ?: return null
        var best: SemanticsNode? = null

        fun visit(node: SemanticsNode) {
            val b = runCatching { node.boundsInRoot }.getOrNull() ?: return
            if (lx < b.left || lx > b.right || ly < b.top || ly > b.bottom) return
            if (isInteractive(node.config) || labelOf(node.config) != null) best = node
            for (child in runCatching { node.children }.getOrDefault(emptyList())) visit(child)
        }
        runCatching { visit(root) }

        val node = best ?: return null
        val label = labelOf(node.config) ?: return null
        return ComposeTapTarget(label, roleOf(node.config))
    }

    private fun semanticsOwner(view: View): SemanticsOwner? {
        if (view.javaClass.name != ANDROID_COMPOSE_VIEW) return null
        return runCatching {
            val field =
                view.javaClass.declaredFields.firstOrNull {
                    SemanticsOwner::class.java.isAssignableFrom(it.type)
                } ?: return null
            field.isAccessible = true
            field.get(view) as? SemanticsOwner
        }.getOrNull()
    }

    private fun isInteractive(config: SemanticsConfiguration): Boolean =
        config.getOrNull(SemanticsActions.OnClick) != null ||
            config.getOrNull(SemanticsProperties.Role) != null

    private fun labelOf(config: SemanticsConfiguration): String? {
        config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()
            ?.takeIf { it.isNotBlank() }?.let { return it }
        config.getOrNull(SemanticsProperties.TestTag)?.takeIf { it.isNotBlank() }?.let { return it }
        config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text
            ?.takeIf { it.isNotBlank() }?.let { return it }
        return null
    }

    private fun roleOf(config: SemanticsConfiguration): String = config.getOrNull(SemanticsProperties.Role)?.toString() ?: "Composable"

    private const val ANDROID_COMPOSE_VIEW = "androidx.compose.ui.platform.AndroidComposeView"
}
