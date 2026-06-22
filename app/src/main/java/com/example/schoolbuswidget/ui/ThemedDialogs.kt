package com.example.schoolbuswidget.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object ThemedDialogs {

    fun builder(context: Context): MaterialAlertDialogBuilder =
        MaterialAlertDialogBuilder(context)

    fun dialogSurfaceColor(context: Context): Int =
        MaterialColors.getColor(
            context,
            com.google.android.material.R.attr.colorSurfaceContainerLow,
            "DialogSurface",
        )

    fun paddedRoot(context: Context, paddingPx: Int): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(paddingPx, paddingPx / 2, paddingPx, 0)
            setBackgroundColor(dialogSurfaceColor(context))
        }
}

fun AlertDialog.applyThemedSurface(
    context: Context,
    onShow: (AlertDialog) -> Unit = {},
) {
    val bg = ThemedDialogs.dialogSurfaceColor(context)
    setOnShowListener {
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        findViewById<View>(com.google.android.material.R.id.parentPanel)
            ?.setBackgroundColor(bg)
        onShow(this)
    }
}
