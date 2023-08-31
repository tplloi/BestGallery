package com.roy.gallery.pro.dialogs

import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.roy.gallery.pro.R
import com.roy.gallery.pro.extensions.config
import com.roy.gallery.pro.extensions.getCachedMedia
import com.roy.gallery.pro.helpers.SHOW_ALL
import com.roy.gallery.pro.helpers.VIEW_TYPE_GRID
import com.roy.gallery.pro.models.Medium
import com.roy.gallery.pro.models.ThumbnailItem
import com.roy.commons.activities.BaseSimpleActivity
import com.roy.commons.ext.beGoneIf
import com.roy.commons.ext.beVisibleIf
import com.roy.commons.ext.setupDialogStuff
import com.roy.commons.views.MyGridLayoutManager
import kotlinx.android.synthetic.main.dlg_medium_picker.view.*

class PickMediumDialog(val activity: BaseSimpleActivity, val path: String, val callback: (path: String) -> Unit) {
    var dialog: AlertDialog
    var shownMedia = ArrayList<ThumbnailItem>()
    val view = activity.layoutInflater.inflate(R.layout.dlg_medium_picker, null)
    val viewType = activity.config.getFolderViewType(if (activity.config.showAll) SHOW_ALL else path)
    var isGridViewType = viewType == VIEW_TYPE_GRID

    init {
        (view.mediaGrid.layoutManager as MyGridLayoutManager).apply {
            orientation = if (activity.config.scrollHorizontally && isGridViewType) RecyclerView.HORIZONTAL else RecyclerView.VERTICAL
            spanCount = if (isGridViewType) activity.config.mediaColumnCnt else 1
        }

        dialog = AlertDialog.Builder(activity)
                .setPositiveButton(R.string.ok, null)
                .setNegativeButton(R.string.cancel, null)
                .setNeutralButton(R.string.other_folder) { dialogInterface, i -> showOtherFolder() }
                .create().apply {
                    activity.setupDialogStuff(view, this, R.string.select_photo)
                }

        activity.getCachedMedia(path) {
            val media = it.filter { it is Medium && !it.isVideo() } as ArrayList
            if (media.isNotEmpty()) {
                activity.runOnUiThread {
                    gotMedia(media)
                }
            }
        }

        com.roy.gallery.pro.asynctasks.GetMediaAsynctask(activity, path, true, false, false) {
            gotMedia(it)
        }.execute()
    }

    private fun showOtherFolder() {
        PickDirectoryDialog(activity, path, true) {
            callback(it)
            dialog.dismiss()
        }
    }

    private fun gotMedia(media: ArrayList<ThumbnailItem>) {
        if (media.hashCode() == shownMedia.hashCode())
            return

        shownMedia = media
        val adapter = com.roy.gallery.pro.adapters.MediaAdapter(activity, shownMedia.clone() as ArrayList<ThumbnailItem>, null, true, false, path, view.mediaGrid, null) {
            if (it is Medium) {
                callback(it.path)
                dialog.dismiss()
            }
        }

        val scrollHorizontally = activity.config.scrollHorizontally && isGridViewType
        val sorting = activity.config.getFileSorting(if (path.isEmpty()) SHOW_ALL else path)
        view.apply {
            mediaGrid.adapter = adapter

            mediaVerticalFastScroller.isHorizontal = false
            mediaVerticalFastScroller.beGoneIf(scrollHorizontally)

            mediaHorizontalFastScroller.isHorizontal = true
            mediaHorizontalFastScroller.beVisibleIf(scrollHorizontally)

            if (scrollHorizontally) {
                mediaHorizontalFastScroller.allowBubbleDisplay = activity.config.showInfoBubble
                mediaHorizontalFastScroller.setViews(mediaGrid) {
                    mediaHorizontalFastScroller.updateBubbleText((media[it] as? Medium)?.getBubbleText(sorting, activity) ?: "")
                }
            } else {
                mediaVerticalFastScroller.allowBubbleDisplay = activity.config.showInfoBubble
                mediaVerticalFastScroller.setViews(mediaGrid) {
                    mediaVerticalFastScroller.updateBubbleText((media[it] as? Medium)?.getBubbleText(sorting, activity) ?: "")
                }
            }
        }
    }
}
