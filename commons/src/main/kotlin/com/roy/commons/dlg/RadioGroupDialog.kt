package com.roy.commons.dlg

import android.annotation.SuppressLint
import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import com.roy.commons.R
import com.roy.commons.ext.onGlobalLayout
import com.roy.commons.ext.setupDialogStuff
import com.roy.commons.models.RadioItem
import kotlinx.android.synthetic.main.dlg_radio_group.view.*
import java.util.*

@SuppressLint("InflateParams")
class RadioGroupDialog(
    val activity: Activity,
    val items: ArrayList<RadioItem>,
    val checkedItemId: Int = -1,
    private val titleId: Int = 0,
    showOKButton: Boolean = false,
    val cancelCallback: (() -> Unit)? = null,
    val callback: (newValue: Any) -> Unit,
) {
    private val dialog: AlertDialog
    private var wasInit = false
    private var selectedItemId = -1

    init {
        val view = activity.layoutInflater.inflate(R.layout.dlg_radio_group, null)
        view.dialogRadioGroup.apply {
            for (i in 0 until items.size) {
                val radioButton = (activity.layoutInflater.inflate(
                    /* resource = */ R.layout.v_radio_button,
                    /* root = */ null
                ) as RadioButton).apply {
                    text = items[i].title
                    isChecked = items[i].id == checkedItemId
                    id = i
                    setOnClickListener { itemSelected(i) }
                }

                if (items[i].id == checkedItemId) {
                    selectedItemId = i
                }

                addView(
                    radioButton,
                    RadioGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
            }
        }

        val builder = AlertDialog.Builder(activity)
            .setOnCancelListener { cancelCallback?.invoke() }

        if (selectedItemId != -1 && showOKButton) {
            builder.setPositiveButton(R.string.ok) { _, _ -> itemSelected(selectedItemId) }
        }

        dialog = builder.create().apply {
            activity.setupDialogStuff(view, this, titleId)
        }

        if (selectedItemId != -1) {
            view.dialogRadioHolder.apply {
                onGlobalLayout {
                    scrollY =
                        view.dialogRadioGroup.findViewById<View>(selectedItemId).bottom - height
                }
            }
        }

        wasInit = true
    }

    private fun itemSelected(checkedId: Int) {
        if (wasInit) {
            callback(items[checkedId].value)
            dialog.dismiss()
        }
    }
}
