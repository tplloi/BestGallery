package com.roy.commons.dlg

import android.annotation.SuppressLint
import android.app.Activity
import android.content.res.Resources
import android.media.ExifInterface
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.roy.commons.R
import com.roy.commons.ext.formatAsResolution
import com.roy.commons.ext.formatDate
import com.roy.commons.ext.formatSize
import com.roy.commons.ext.getExifCameraModel
import com.roy.commons.ext.getExifDateTaken
import com.roy.commons.ext.getExifProperties
import com.roy.commons.ext.getFilenameFromPath
import com.roy.commons.ext.getLongValue
import com.roy.commons.ext.isAudioSlow
import com.roy.commons.ext.isImageSlow
import com.roy.commons.ext.isVideoSlow
import com.roy.commons.ext.setupDialogStuff
import com.roy.commons.ext.toast
import com.roy.commons.helpers.sumByInt
import com.roy.commons.helpers.sumByLong
import com.roy.commons.models.FileDirItem
import kotlinx.android.synthetic.main.dlg_properties.view.*
import kotlinx.android.synthetic.main.v_property_item.view.*
import java.io.File
import java.io.FileNotFoundException
import java.util.*

class PropertiesDialog() {
    private lateinit var mInflater: LayoutInflater
    private lateinit var mPropertyView: ViewGroup
    private lateinit var mResources: Resources

    /**
     * A File Properties dialog constructor with an optional parameter, usable at 1 file selected
     *
     * @param activity request activity to avoid some Theme.AppCompat issues
     * @param path the file path
     * @param countHiddenItems toggle determining if we will count hidden files themselves and their sizes (reasonable only at directory properties)
     */
    @SuppressLint("InflateParams", "MissingInflatedId")
    constructor(activity: Activity, path: String, countHiddenItems: Boolean = false) : this() {
        if (!File(path).exists()) {
            activity.toast(
                String.format(
                    activity.getString(R.string.source_file_doesnt_exist),
                    path
                )
            )
            return
        }

        mInflater = LayoutInflater.from(activity)
        mResources = activity.resources
        val view = mInflater.inflate(R.layout.dlg_properties, null)
        mPropertyView = view.propertiesHolder

        val fileDirItem = FileDirItem(path, path.getFilenameFromPath(), File(path).isDirectory)
        addProperty(R.string.name, fileDirItem.name)
        addProperty(R.string.path, fileDirItem.getParentPath())
        addProperty(R.string.size, "…", R.id.properties_size)

        Thread {
            val fileCount = fileDirItem.getProperFileCount(countHiddenItems)
            val size = fileDirItem.getProperSize(countHiddenItems).formatSize()
            activity.runOnUiThread {
                view.findViewById<TextView>(R.id.properties_size).propertyValue.text = size

                if (fileDirItem.isDirectory) {
                    view.findViewById<TextView>(R.id.properties_file_count).propertyValue.text =
                        fileCount.toString()
                }
            }

            if (!fileDirItem.isDirectory) {
                val projection = arrayOf(MediaStore.Images.Media.DATE_MODIFIED)
                val uri = MediaStore.Files.getContentUri("external")
                val selection = "${MediaStore.MediaColumns.DATA} = ?"
                val selectionArgs = arrayOf(path)
                val cursor =
                    activity.contentResolver.query(
                        /* uri = */ uri,
                        /* projection = */ projection,
                        /* selection = */ selection,
                        /* selectionArgs = */ selectionArgs,
                        /* sortOrder = */ null
                    )
                cursor?.use {
                    if (cursor.moveToFirst()) {
                        val dateModified =
                            cursor.getLongValue(MediaStore.Images.Media.DATE_MODIFIED) * 1000L
                        updateLastModified(
                            activity = activity,
                            view = view,
                            timestamp = dateModified
                        )
                    } else {
                        updateLastModified(
                            activity = activity,
                            view = view,
                            timestamp = fileDirItem.getLastModified()
                        )
                    }
                }
            }
        }.start()

        when {
            fileDirItem.isDirectory -> {
                addProperty(
                    R.string.direct_children_count,
                    fileDirItem.getDirectChildrenCount(countHiddenItems).toString()
                )
                addProperty(R.string.files_count, "…", R.id.properties_file_count)
            }

            fileDirItem.path.isImageSlow() -> {
                fileDirItem.getResolution()
                    ?.let { addProperty(R.string.resolution, it.formatAsResolution()) }
            }

            fileDirItem.path.isAudioSlow() -> {
                fileDirItem.getDuration()?.let { addProperty(R.string.duration, it) }
                fileDirItem.getSongTitle()?.let { addProperty(R.string.song_title, it) }
                fileDirItem.getArtist()?.let { addProperty(R.string.artist, it) }
                fileDirItem.getAlbum()?.let { addProperty(R.string.album, it) }
            }

            fileDirItem.path.isVideoSlow() -> {
                fileDirItem.getDuration()?.let { addProperty(R.string.duration, it) }
                fileDirItem.getResolution()
                    ?.let { addProperty(R.string.resolution, it.formatAsResolution()) }
                fileDirItem.getArtist()?.let { addProperty(R.string.artist, it) }
                fileDirItem.getAlbum()?.let { addProperty(R.string.album, it) }
            }
        }

        if (fileDirItem.isDirectory) {
            addProperty(R.string.last_modified, fileDirItem.getLastModified().formatDate(activity))
        } else {
            addProperty(R.string.last_modified, "…", R.id.properties_last_modified)
            try {
                addExifProperties(path, activity)
            } catch (e: FileNotFoundException) {
                activity.toast(R.string.unknown_error_occurred)
                return
            }
        }

        AlertDialog.Builder(activity)
            .setPositiveButton(R.string.ok, null)
            .create().apply {
                activity.setupDialogStuff(view = view, dialog = this, titleId = R.string.properties)
            }
    }

    private fun updateLastModified(activity: Activity, view: View, timestamp: Long) {
        activity.runOnUiThread {
            view.findViewById<TextView>(R.id.properties_last_modified).propertyValue.text =
                timestamp.formatDate(activity)
        }
    }

    /**
     * A File Properties dialog constructor with an optional parameter, usable at multiple items selected
     *
     * @param activity request activity to avoid some Theme.AppCompat issues
     * @param countHiddenItems toggle determining if we will count hidden files themselves and their sizes
     */
    @SuppressLint("MissingInflatedId", "InflateParams")
    constructor(
        activity: Activity,
        paths: List<String>,
        countHiddenItems: Boolean = false,
    ) : this() {
        mInflater = LayoutInflater.from(activity)
        mResources = activity.resources
        val view = mInflater.inflate(R.layout.dlg_properties, null)
        mPropertyView = view.propertiesHolder

        val fileDirItems = ArrayList<FileDirItem>(paths.size)
        paths.forEach {
            val fileDirItem = FileDirItem(it, it.getFilenameFromPath(), File(it).isDirectory)
            fileDirItems.add(fileDirItem)
        }

        val isSameParent = isSameParent(fileDirItems)

        addProperty(R.string.items_selected, paths.size.toString())
        if (isSameParent) {
            addProperty(R.string.path, fileDirItems[0].getParentPath())
        }

        addProperty(R.string.size, "…", R.id.properties_size)
        addProperty(R.string.files_count, "…", R.id.properties_file_count)

        Thread {
            val fileCount = fileDirItems.sumByInt { it.getProperFileCount(countHiddenItems) }
            val size = fileDirItems.sumByLong { it.getProperSize(countHiddenItems) }.formatSize()
            activity.runOnUiThread {
                view.findViewById<TextView>(R.id.properties_size).propertyValue.text = size
                view.findViewById<TextView>(R.id.properties_file_count).propertyValue.text =
                    fileCount.toString()
            }
        }.start()

        AlertDialog.Builder(activity)
            .setPositiveButton(R.string.ok, null)
            .create().apply {
                activity.setupDialogStuff(view, this, R.string.properties)
            }
    }

    private fun addExifProperties(path: String, activity: Activity) {
        val exif = ExifInterface(path)
        val dateTaken = getExifDateTaken(exif, activity)
        if (dateTaken.isNotEmpty()) {
            addProperty(R.string.date_taken, dateTaken)
        }

        val cameraModel = getExifCameraModel(exif)
        if (cameraModel.isNotEmpty()) {
            addProperty(R.string.camera, cameraModel)
        }

        val exifString = getExifProperties(exif)
        if (exifString.isNotEmpty()) {
            addProperty(R.string.exif, exifString)
        }
    }

    private fun isSameParent(fileDirItems: List<FileDirItem>): Boolean {
        var parent = fileDirItems[0].getParentPath()
        for (file in fileDirItems) {
            val curParent = file.getParentPath()
            if (curParent != parent) {
                return false
            }

            parent = curParent
        }
        return true
    }

    private fun addProperty(labelId: Int, value: String?, viewId: Int = 0) {
        if (value == null)
            return

        mInflater.inflate(R.layout.v_property_item, mPropertyView, false).apply {
            propertyLabel.text = mResources.getString(labelId)
            propertyValue.text = value
            mPropertyView.propertiesHolder.addView(this)

            if (viewId != 0) {
                id = viewId
            }
        }
    }
}
