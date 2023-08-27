package com.eagle.commons.dialogs

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import com.eagle.commons.R
import com.eagle.commons.activities.BaseSimpleActivity
import com.eagle.commons.ext.*
import kotlinx.android.synthetic.main.dlg_radio_group.view.*

/**
 * A dialog for choosing between internal, root, SD card (optional) storage
 *
 * @param activity has to be activity to avoid some Theme.AppCompat issues
 * @param currPath current path to decide which storage should be preselected
 * @param callback an anonymous function
 *
 */
class StoragePickerDialog(val activity: BaseSimpleActivity, currPath: String, val callback: (pickedPath: String) -> Unit) {
    private val ID_INTERNAL = 1
    private val ID_SD = 2
    private val ID_OTG = 3
    private val ID_ROOT = 4

    private var mDialog: AlertDialog
    private var radioGroup: RadioGroup
    private var defaultSelectedId = 0

    init {
        val inflater = LayoutInflater.from(activity)
        val resources = activity.resources
        val layoutParams = RadioGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        val view = inflater.inflate(R.layout.dlg_radio_group, null)
        radioGroup = view.dialogRadioGroup
        val basePath = currPath.getBasePath(activity)

        val internalButton = inflater.inflate(R.layout.v_radio_button, null) as RadioButton
        internalButton.apply {
            id = ID_INTERNAL
            text = resources.getString(R.string.internal)
            isChecked = basePath == context.internalStoragePath
            setOnClickListener { internalPicked() }
            if (isChecked) {
                defaultSelectedId = id
            }
        }
        radioGroup.addView(internalButton, layoutParams)

        if (activity.hasExternalSDCard()) {
            val sdButton = inflater.inflate(R.layout.v_radio_button, null) as RadioButton
            sdButton.apply {
                id = ID_SD
                text = resources.getString(R.string.sd_card)
                isChecked = basePath == context.sdCardPath
                setOnClickListener { sdPicked() }
                if (isChecked) {
                    defaultSelectedId = id
                }
            }
            radioGroup.addView(sdButton, layoutParams)
        }

        if (activity.hasOTGConnected()) {
            val otgButton = inflater.inflate(R.layout.v_radio_button, null) as RadioButton
            otgButton.apply {
                id = ID_OTG
                text = resources.getString(R.string.usb)
                isChecked = basePath == context.otgPath
                setOnClickListener { otgPicked() }
                if (isChecked) {
                    defaultSelectedId = id
                }
            }
            radioGroup.addView(otgButton, layoutParams)
        }

        val rootButton = inflater.inflate(R.layout.v_radio_button, null) as RadioButton
        rootButton.apply {
            id = ID_ROOT
            text = resources.getString(R.string.root)
            isChecked = basePath == "/"
            setOnClickListener { rootPicked() }
            if (isChecked) {
                defaultSelectedId = id
            }
        }
        radioGroup.addView(rootButton, layoutParams)

        mDialog = AlertDialog.Builder(activity)
                .create().apply {
                    activity.setupDialogStuff(view, this, R.string.select_storage)
                }
    }

    private fun internalPicked() {
        mDialog.dismiss()
        callback(activity.internalStoragePath)
    }

    private fun sdPicked() {
        mDialog.dismiss()
        callback(activity.sdCardPath)
    }

    private fun otgPicked() {
        if (activity.baseConfig.OTGPath.isEmpty()) {
            activity.getStorageDirectories().firstOrNull { it.trimEnd('/') != activity.internalStoragePath && it.trimEnd('/') != activity.sdCardPath }?.apply {
                activity.baseConfig.OTGPath = trimEnd('/')
            }
        }

        mDialog.dismiss()
        callback(activity.otgPath)
    }

    private fun rootPicked() {
        mDialog.dismiss()
        callback("/")
    }
}
