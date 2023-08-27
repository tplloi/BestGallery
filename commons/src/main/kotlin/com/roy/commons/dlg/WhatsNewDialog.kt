package com.roy.commons.dlg

import android.annotation.SuppressLint
import android.app.Activity
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import com.roy.commons.R
import com.roy.commons.ext.setupDialogStuff
import com.roy.commons.models.Release
import kotlinx.android.synthetic.main.dlg_whats_new.view.*

@SuppressLint("InflateParams")
class WhatsNewDialog(val activity: Activity, private val releases: List<Release>) {
    init {
        val view = LayoutInflater.from(activity).inflate(R.layout.dlg_whats_new, null)
        view.whatsNewContent.text = getNewReleases()

        AlertDialog.Builder(activity)
            .setPositiveButton(R.string.ok, null)
            .create().apply {
                activity.setupDialogStuff(view = view, dialog = this, titleId = R.string.whats_new)
            }
    }

    private fun getNewReleases(): String {
        val sb = StringBuilder()

        releases.forEach {
            val parts = activity.getString(it.textId).split("\n").map(String::trim)
            parts.forEach {
                sb.append("- $it\n")
            }
        }

        return sb.toString()
    }
}
