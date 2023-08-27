package com.eagle.commons.dialogs

import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import com.eagle.commons.R
import com.eagle.commons.activities.BaseSimpleActivity
import com.eagle.commons.ext.*
import com.eagle.commons.itf.LineColorPickerListener
import kotlinx.android.synthetic.main.dlg_line_color_picker.view.*
import java.util.*

class LineColorPickerDialog(
    val activity: BaseSimpleActivity,
    val color: Int,
    val isPrimaryColorPicker: Boolean,
    private val primaryColors: Int = R.array.md_primary_colors,
    private val appIconIDs: ArrayList<Int>? = null,
    val callback: (wasPositivePressed: Boolean, color: Int) -> Unit,
) {

    private val PRIMARY_COLORS_COUNT = 19
    private val DEFAULT_PRIMARY_COLOR_INDEX = 2
    private val DEFAULT_SECONDARY_COLOR_INDEX = 6
    private val DEFAULT_COLOR_VALUE = activity.resources.getColor(R.color.color_primary)

    private var wasDimmedBackgroundRemoved = false
    private var dialog: AlertDialog? = null
    private var view: View = activity.layoutInflater.inflate(R.layout.dlg_line_color_picker, null)

    init {
        view.apply {
            hexCode.text = color.toHex()
            hexCode.setOnLongClickListener {
                activity.copyToClipboard(hexCode.value.substring(1))
                true
            }

            lineColorPickerIcon.beGoneIf(isPrimaryColorPicker)
            val indexes = getColorIndexes(color)

            val primaryColorIndex = indexes.first
            primaryColorChanged(primaryColorIndex)
            primaryLineColorPicker.updateColors(getColors(primaryColors), primaryColorIndex)
            primaryLineColorPicker.listener = object : LineColorPickerListener {
                override fun colorChanged(index: Int, color: Int) {
                    val secondaryColors = getColorsForIndex(index)
                    secondaryLineColorPicker.updateColors(secondaryColors)

                    val newColor =
                        if (isPrimaryColorPicker) secondaryLineColorPicker.getCurrentColor() else color
                    colorUpdated(newColor)

                    if (!isPrimaryColorPicker) {
                        primaryColorChanged(index)
                    }
                }
            }

            secondaryLineColorPicker.beVisibleIf(isPrimaryColorPicker)
            secondaryLineColorPicker.updateColors(
                getColorsForIndex(primaryColorIndex),
                indexes.second
            )
            secondaryLineColorPicker.listener = object : LineColorPickerListener {
                override fun colorChanged(index: Int, color: Int) {
                    colorUpdated(color)
                }
            }
        }

        dialog = AlertDialog.Builder(activity)
            .setPositiveButton(R.string.ok) { _, _ -> dialogConfirmed() }
            .setNegativeButton(R.string.cancel) { _, _ -> dialogDismissed() }
            .setOnCancelListener { dialogDismissed() }
            .create().apply {
                activity.setupDialogStuff(view, this)
            }
    }

    fun getSpecificColor() = view.secondaryLineColorPicker.getCurrentColor()

    private fun colorUpdated(color: Int) {
        view.hexCode.text = color.toHex()
        if (isPrimaryColorPicker) {
            activity.updateActionbarColor(color)
            activity.setTheme(activity.getThemeId(color))

            if (!wasDimmedBackgroundRemoved) {
                dialog?.window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                wasDimmedBackgroundRemoved = true
            }
        }
    }

    private fun getColorIndexes(color: Int): Pair<Int, Int> {
        if (color == DEFAULT_COLOR_VALUE) {
            return getDefaultColorPair()
        }

        for (i in 0 until PRIMARY_COLORS_COUNT) {
            getColorsForIndex(i).indexOfFirst { color == it }.apply {
                if (this != -1) {
                    return Pair(i, this)
                }
            }
        }

        return getDefaultColorPair()
    }

    private fun primaryColorChanged(index: Int) {
        view.lineColorPickerIcon.setImageResource(appIconIDs?.getOrNull(index) ?: 0)
    }

    private fun getDefaultColorPair() =
        Pair(DEFAULT_PRIMARY_COLOR_INDEX, DEFAULT_SECONDARY_COLOR_INDEX)

    private fun dialogDismissed() {
        callback(false, 0)
    }

    private fun dialogConfirmed() {
        val targetView =
            if (isPrimaryColorPicker) view.secondaryLineColorPicker else view.primaryLineColorPicker
        val color = targetView.getCurrentColor()
        callback(true, color)
    }

    private fun getColorsForIndex(index: Int) = when (index) {
        0 -> getColors(R.array.md_reds)
        1 -> getColors(R.array.md_pinks)
        2 -> getColors(R.array.md_purples)
        3 -> getColors(R.array.md_deep_purples)
        4 -> getColors(R.array.md_indigos)
        5 -> getColors(R.array.md_blues)
        6 -> getColors(R.array.md_light_blues)
        7 -> getColors(R.array.md_cyans)
        8 -> getColors(R.array.md_teals)
        9 -> getColors(R.array.md_greens)
        10 -> getColors(R.array.md_light_greens)
        11 -> getColors(R.array.md_limes)
        12 -> getColors(R.array.md_yellows)
        13 -> getColors(R.array.md_ambers)
        14 -> getColors(R.array.md_oranges)
        15 -> getColors(R.array.md_deep_oranges)
        16 -> getColors(R.array.md_browns)
        17 -> getColors(R.array.md_blue_greys)
        18 -> getColors(R.array.md_greys)
        else -> throw RuntimeException("Invalid color id $index")
    }

    private fun getColors(id: Int) = activity.resources.getIntArray(id).toCollection(ArrayList())
}
