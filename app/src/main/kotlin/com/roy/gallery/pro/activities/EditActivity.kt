package com.roy.gallery.pro.activities

import android.annotation.TargetApi
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.graphics.Color
import android.graphics.Point
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuItem
import android.widget.RelativeLayout
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.roy.gallery.pro.BuildConfig
import com.roy.gallery.pro.R
import com.roy.gallery.pro.adapters.FiltersAdapter
import com.roy.gallery.pro.dialogs.OtherAspectRatioDialog
import com.roy.gallery.pro.dialogs.ResizeDialog
import com.roy.gallery.pro.dialogs.SaveAsDialog
import com.roy.gallery.pro.extensions.config
import com.roy.gallery.pro.extensions.openEditor
import com.roy.gallery.pro.helpers.*
import com.roy.gallery.pro.models.FilterItem
import com.roy.commons.dlg.ColorPickerDialog
import com.roy.commons.ext.applyColorFilter
import com.roy.commons.ext.beGone
import com.roy.commons.ext.beGoneIf
import com.roy.commons.ext.beVisible
import com.roy.commons.ext.beVisibleIf
import com.roy.commons.ext.copyTo
import com.roy.commons.ext.getAdjustedPrimaryColor
import com.roy.commons.ext.getCompressionFormat
import com.roy.commons.ext.getCurrentFormattedDateTime
import com.roy.commons.ext.getFileOutputStream
import com.roy.commons.ext.getFilenameExtension
import com.roy.commons.ext.getFilenameFromContentUri
import com.roy.commons.ext.getFilenameFromPath
import com.roy.commons.ext.getParentPath
import com.roy.commons.ext.getRealPathFromURI
import com.roy.commons.ext.internalStoragePath
import com.roy.commons.ext.isGone
import com.roy.commons.ext.isPathOnOTG
import com.roy.commons.ext.isVisible
import com.roy.commons.ext.onGlobalLayout
import com.roy.commons.ext.onSeekBarChangeListener
import com.roy.commons.ext.scanPathRecursively
import com.roy.commons.ext.sharePathIntent
import com.roy.commons.ext.showErrorToast
import com.roy.commons.ext.showSideloadingDialog
import com.roy.commons.ext.toast
import com.roy.commons.helpers.PERMISSION_WRITE_STORAGE
import com.roy.commons.helpers.REAL_FILE_PATH
import com.roy.commons.helpers.SIDELOADING_TRUE
import com.roy.commons.helpers.isNougatPlus
import com.roy.commons.models.FileDirItem
import com.theartofdev.edmodo.cropper.CropImageView
import com.zomato.photofilters.FilterPack
import com.zomato.photofilters.imageprocessors.Filter
import kotlinx.android.synthetic.main.a_edit.*
import kotlinx.android.synthetic.main.v_bottom_actions_aspect_ratio.*
import kotlinx.android.synthetic.main.v_bottom_editor_actions_filter.*
import kotlinx.android.synthetic.main.v_bottom_editor_crop_rotate_actions.*
import kotlinx.android.synthetic.main.v_bottom_editor_draw_actions.*
import kotlinx.android.synthetic.main.v_bottom_editor_primary_actions.*
import java.io.*

class EditActivity : SimpleActivity(), CropImageView.OnCropImageCompleteListener {
    companion object {
        init {
            System.loadLibrary("NativeImageProcessor")
        }
    }

    private val TEMP_FOLDER_NAME = "images"
    private val ASPECT_X = "aspectX"
    private val ASPECT_Y = "aspectY"
    private val CROP = "crop"

    // constants for bottom primary action groups
    private val PRIMARY_ACTION_NONE = 0
    private val PRIMARY_ACTION_FILTER = 1
    private val PRIMARY_ACTION_CROP_ROTATE = 2
    private val PRIMARY_ACTION_DRAW = 3

    private val CROP_ROTATE_NONE = 0
    private val CROP_ROTATE_ASPECT_RATIO = 1

    private lateinit var uri: Uri
    private lateinit var saveUri: Uri
    private var resizeWidth = 0
    private var resizeHeight = 0
    private var drawColor = 0
    private var lastOtherAspectRatio: Pair<Int, Int>? = null
    private var currPrimaryAction = PRIMARY_ACTION_NONE
    private var currCropRotateAction = CROP_ROTATE_NONE
    private var currAspectRatio = ASPECT_RATIO_FREE
    private var isCropIntent = false
    private var isEditingWithThirdParty = false
    private var isSharingBitmap = false
    private var wasDrawCanvasPositioned = false
    private var oldExif: ExifInterface? = null
    private var filterInitialBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.a_edit)

        if (config.appSideloadingStatus == SIDELOADING_TRUE) {
            showSideloadingDialog()
            return
        }

        handlePermission(PERMISSION_WRITE_STORAGE) {
            if (it) {
                initEditActivity()
            } else {
                toast(R.string.no_storage_permissions)
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isEditingWithThirdParty = false
        bottomDrawWidth.setColors(
            config.textColor,
            getAdjustedPrimaryColor(),
            config.backgroundColor
        )
    }

    override fun onStop() {
        super.onStop()
        if (isEditingWithThirdParty) {
            finish()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_editor, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.saveAs -> saveImage()
            R.id.edit -> editWith()
            R.id.share -> shareImage()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun initEditActivity() {
        if (intent.data == null) {
            toast(R.string.invalid_image_path)
            finish()
            return
        }

        uri = intent.data!!
        if (uri.scheme != "file" && uri.scheme != "content") {
            toast(R.string.unknown_file_location)
            finish()
            return
        }

        if (intent.extras?.containsKey(REAL_FILE_PATH) == true) {
            val realPath = intent.extras!!.getString(REAL_FILE_PATH) ?: ""
            uri = when {
                isPathOnOTG(realPath) -> uri
                realPath.startsWith("file:/") -> Uri.parse(realPath)
                else -> Uri.fromFile(File(realPath))
            }
        } else {
            (getRealPathFromURI(uri))?.apply {
                uri = Uri.fromFile(File(this))
            }
        }

        saveUri = when {
            intent.extras?.containsKey(MediaStore.EXTRA_OUTPUT) == true -> intent.extras!!.get(
                MediaStore.EXTRA_OUTPUT
            ) as Uri

            else -> uri
        }

        isCropIntent = intent.extras?.get(CROP) == "true"
        if (isCropIntent) {
            bottomEditorPrimaryActions.beGone()
            (bottomEditorCropRotateActions.layoutParams as RelativeLayout.LayoutParams).addRule(
                RelativeLayout.ALIGN_PARENT_BOTTOM,
                1
            )
        }

        loadDefaultImageView()
        setupBottomActions()

        if (config.lastEditorCropAspectRatio == ASPECT_RATIO_OTHER) {
            if (config.lastEditorCropOtherAspectRatioX == 0) {
                config.lastEditorCropOtherAspectRatioX = 1
            }

            if (config.lastEditorCropOtherAspectRatioY == 0) {
                config.lastEditorCropOtherAspectRatioY = 1
            }

            lastOtherAspectRatio =
                Pair(config.lastEditorCropOtherAspectRatioX, config.lastEditorCropOtherAspectRatioY)
        }
        updateAspectRatio(config.lastEditorCropAspectRatio)
    }

    private fun loadDefaultImageView() {
        defaultImageView.beVisible()
        cropImageView.beGone()
        editorDrawCanvas.beGone()

        val options = RequestOptions()
            .skipMemoryCache(true)
            .diskCacheStrategy(DiskCacheStrategy.NONE)

        Glide.with(this)
            .asBitmap()
            .load(uri)
            .apply(options)
            .listener(object : RequestListener<Bitmap> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Bitmap>?,
                    isFirstResource: Boolean,
                ) = false

                override fun onResourceReady(
                    bitmap: Bitmap?,
                    model: Any?,
                    target: Target<Bitmap>?,
                    dataSource: DataSource?,
                    isFirstResource: Boolean,
                ): Boolean {
                    val currentFilter = getFiltersAdapter()?.getCurrentFilter()
                    if (filterInitialBitmap == null) {
                        loadCropImageView()
                        bottomCropRotateClicked()
                    }

                    if (filterInitialBitmap != null && currentFilter != null && currentFilter.filter.name != getString(
                            R.string.none
                        )
                    ) {
                        defaultImageView.onGlobalLayout {
                            applyFilter(currentFilter)
                        }
                    } else {
                        filterInitialBitmap = bitmap
                    }

                    if (isCropIntent) {
                        bottomPrimaryFilter.beGone()
                        bottomPrimaryDraw.beGone()
                    }

                    return false
                }
            }).into(defaultImageView)
    }

    private fun loadCropImageView() {
        defaultImageView.beGone()
        editorDrawCanvas.beGone()
        cropImageView.apply {
            beVisible()
            setOnCropImageCompleteListener(this@EditActivity)
            setImageUriAsync(uri)
            guidelines = CropImageView.Guidelines.ON

            if (isCropIntent && shouldCropSquare()) {
                currAspectRatio = ASPECT_RATIO_ONE_ONE
                setFixedAspectRatio(true)
                bottomAspectRatio.beGone()
            }
        }
    }

    private fun loadDrawCanvas() {
        defaultImageView.beGone()
        cropImageView.beGone()
        editorDrawCanvas.beVisible()

        if (!wasDrawCanvasPositioned) {
            wasDrawCanvasPositioned = true
            editorDrawCanvas.onGlobalLayout {
                Thread {
                    fillCanvasBackground()
                }.start()
            }
        }
    }

    private fun fillCanvasBackground() {
        val size = Point()
        windowManager.defaultDisplay.getSize(size)
        val options = RequestOptions()
            .format(DecodeFormat.PREFER_ARGB_8888)
            .skipMemoryCache(true)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .fitCenter()

        try {
            val builder = Glide.with(applicationContext)
                .asBitmap()
                .load(uri)
                .apply(options)
                .into(editorDrawCanvas.width, editorDrawCanvas.height)

            val bitmap = builder.get()
            runOnUiThread {
                editorDrawCanvas.apply {
                    updateBackgroundBitmap(bitmap)
                    layoutParams.width = bitmap.width
                    layoutParams.height = bitmap.height
                    y = (height - bitmap.height) / 2f
                    requestLayout()
                }
            }
        } catch (e: Exception) {
            showErrorToast(e)
        }
    }

    @TargetApi(Build.VERSION_CODES.N)
    private fun saveImage() {
        var inputStream: InputStream? = null
        try {
            if (isNougatPlus()) {
                inputStream = contentResolver.openInputStream(uri)
                oldExif = inputStream?.let { ExifInterface(it) }
            }
        } catch (e: Exception) {
        } finally {
            inputStream?.close()
        }

        if (cropImageView.isVisible()) {
            cropImageView.getCroppedImageAsync()
        } else if (editorDrawCanvas.isVisible()) {
            val bitmap = editorDrawCanvas.getBitmap()
            if (saveUri.scheme == "file") {
                saveUri.path?.let {
                    SaveAsDialog(this, it, true) {
                        saveBitmapToFile(bitmap, it, true)
                    }
                }
            } else if (saveUri.scheme == "content") {
                val filePathGetter = getNewFilePath()
                SaveAsDialog(this, filePathGetter.first, filePathGetter.second) {
                    saveBitmapToFile(bitmap, it, true)
                }
            }
        } else {
            val currentFilter = getFiltersAdapter()?.getCurrentFilter() ?: return
            val filePathGetter = getNewFilePath()
            SaveAsDialog(this, filePathGetter.first, filePathGetter.second) {
                toast(R.string.saving)

                // clean up everything to free as much memory as possible
                defaultImageView.setImageResource(0)
                cropImageView.setImageBitmap(null)
                bottomActionsFilterList.adapter = null
                bottomActionsFilterList.beGone()

                Thread {
                    try {
                        val originalBitmap = Glide.with(applicationContext).asBitmap().load(uri)
                            .submit(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL).get()
                        currentFilter.filter.processFilter(originalBitmap)
                        saveBitmapToFile(originalBitmap, it, false)
                    } catch (e: OutOfMemoryError) {
                        toast(R.string.out_of_memory_error)
                    }
                }.start()
            }
        }
    }

    private fun shareImage() {
        Thread {
            when {
                defaultImageView.isVisible() -> {
                    val currentFilter = getFiltersAdapter()?.getCurrentFilter()
                    if (currentFilter == null) {
                        toast(R.string.unknown_error_occurred)
                        return@Thread
                    }

                    val originalBitmap = Glide.with(applicationContext).asBitmap().load(uri)
                        .submit(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL).get()
                    currentFilter.filter.processFilter(originalBitmap)
                    shareBitmap(originalBitmap)
                }

                cropImageView.isVisible() -> {
                    isSharingBitmap = true
                    runOnUiThread {
                        cropImageView.getCroppedImageAsync()
                    }
                }

                editorDrawCanvas.isVisible() -> shareBitmap(editorDrawCanvas.getBitmap())
            }
        }.start()
    }

    private fun getTempImagePath(bitmap: Bitmap, callback: (path: String?) -> Unit) {
        val bytes = ByteArrayOutputStream()
        bitmap.compress(CompressFormat.PNG, 0, bytes)

        val folder = File(cacheDir, TEMP_FOLDER_NAME)
        if (!folder.exists()) {
            if (!folder.mkdir()) {
                callback(null)
                return
            }
        }

        val filename = applicationContext.getFilenameFromContentUri(saveUri) ?: "tmp.jpg"
        val newPath = "$folder/$filename"
        val fileDirItem = FileDirItem(newPath, filename)
        getFileOutputStream(fileDirItem, true) {
            if (it != null) {
                try {
                    it.write(bytes.toByteArray())
                    callback(newPath)
                } catch (e: Exception) {
                } finally {
                    it.close()
                }
            } else {
                callback("")
            }
        }
    }

    private fun shareBitmap(bitmap: Bitmap) {
        getTempImagePath(bitmap) {
            if (it != null) {
                sharePathIntent(it, BuildConfig.APPLICATION_ID)
            } else {
                toast(R.string.unknown_error_occurred)
            }
        }
    }

    private fun getFiltersAdapter() = bottomActionsFilterList.adapter as? FiltersAdapter

    private fun setupBottomActions() {
        setupPrimaryActionButtons()
        setupCropRotateActionButtons()
        setupAspectRatioButtons()
        setupDrawButtons()
    }

    private fun setupPrimaryActionButtons() {
        bottomPrimaryFilter.setOnClickListener {
            bottomFilterClicked()
        }

        bottomPrimaryCropRotate.setOnClickListener {
            bottomCropRotateClicked()
        }

        bottomPrimaryDraw.setOnClickListener {
            bottomDrawClicked()
        }
    }

    private fun bottomFilterClicked() {
        currPrimaryAction = if (currPrimaryAction == PRIMARY_ACTION_FILTER) {
            PRIMARY_ACTION_NONE
        } else {
            PRIMARY_ACTION_FILTER
        }
        updatePrimaryActionButtons()
    }

    private fun bottomCropRotateClicked() {
        currPrimaryAction = if (currPrimaryAction == PRIMARY_ACTION_CROP_ROTATE) {
            PRIMARY_ACTION_NONE
        } else {
            PRIMARY_ACTION_CROP_ROTATE
        }
        updatePrimaryActionButtons()
    }

    private fun bottomDrawClicked() {
        currPrimaryAction = if (currPrimaryAction == PRIMARY_ACTION_DRAW) {
            PRIMARY_ACTION_NONE
        } else {
            PRIMARY_ACTION_DRAW
        }
        updatePrimaryActionButtons()
    }

    private fun setupCropRotateActionButtons() {
        bottomRotate.setOnClickListener {
            cropImageView.rotateImage(90)
        }

        bottomResize.beGoneIf(isCropIntent)
        bottomResize.setOnClickListener {
            resizeImage()
        }

        bottomFlipHorizontally.setOnClickListener {
            cropImageView.flipImageHorizontally()
        }

        bottomFlipVertically.setOnClickListener {
            cropImageView.flipImageVertically()
        }

        bottomAspectRatio.setOnClickListener {
            currCropRotateAction = if (currCropRotateAction == CROP_ROTATE_ASPECT_RATIO) {
                cropImageView.guidelines = CropImageView.Guidelines.OFF
                bottomAspectRatios.beGone()
                CROP_ROTATE_NONE
            } else {
                cropImageView.guidelines = CropImageView.Guidelines.ON
                bottomAspectRatios.beVisible()
                CROP_ROTATE_ASPECT_RATIO
            }
            updateCropRotateActionButtons()
        }
    }

    private fun setupAspectRatioButtons() {
        bottomAspectRatioFree.setOnClickListener {
            updateAspectRatio(ASPECT_RATIO_FREE)
        }

        bottomAspectRatioOneOne.setOnClickListener {
            updateAspectRatio(ASPECT_RATIO_ONE_ONE)
        }

        bottomAspectRatioFourThree.setOnClickListener {
            updateAspectRatio(ASPECT_RATIO_FOUR_THREE)
        }

        bottomAspectRatioSixteenNine.setOnClickListener {
            updateAspectRatio(ASPECT_RATIO_SIXTEEN_NINE)
        }

        bottomAspectRatioOther.setOnClickListener {
            OtherAspectRatioDialog(this, lastOtherAspectRatio) {
                lastOtherAspectRatio = it
                config.lastEditorCropOtherAspectRatioX = it.first
                config.lastEditorCropOtherAspectRatioY = it.second
                updateAspectRatio(ASPECT_RATIO_OTHER)
            }
        }

        updateAspectRatioButtons()
    }

    private fun setupDrawButtons() {
        updateDrawColor(config.lastEditorDrawColor)
        bottomDrawWidth.progress = config.lastEditorBrushSize
        updateBrushSize(config.lastEditorBrushSize)

        bottomDrawColorClickable.setOnClickListener {
            ColorPickerDialog(this, drawColor) { wasPositivePressed, color ->
                if (wasPositivePressed) {
                    updateDrawColor(color)
                }
            }
        }

        bottomDrawWidth.onSeekBarChangeListener {
            config.lastEditorBrushSize = it
            updateBrushSize(it)
        }

        bottomDrawUndo.setOnClickListener {
            editorDrawCanvas.undo()
        }
    }

    private fun updateBrushSize(percent: Int) {
        editorDrawCanvas.updateBrushSize(percent)
        val scale = Math.max(0.03f, percent / 100f)
        bottomDrawColor.scaleX = scale
        bottomDrawColor.scaleY = scale
    }

    private fun updatePrimaryActionButtons() {
        if (cropImageView.isGone() && currPrimaryAction == PRIMARY_ACTION_CROP_ROTATE) {
            loadCropImageView()
        } else if (defaultImageView.isGone() && currPrimaryAction == PRIMARY_ACTION_FILTER) {
            loadDefaultImageView()
        } else if (editorDrawCanvas.isGone() && currPrimaryAction == PRIMARY_ACTION_DRAW) {
            loadDrawCanvas()
        }

        arrayOf(bottomPrimaryFilter, bottomPrimaryCropRotate, bottomPrimaryDraw).forEach {
            it.applyColorFilter(Color.WHITE)
        }

        val currentPrimaryActionButton = when (currPrimaryAction) {
            PRIMARY_ACTION_FILTER -> bottomPrimaryFilter
            PRIMARY_ACTION_CROP_ROTATE -> bottomPrimaryCropRotate
            PRIMARY_ACTION_DRAW -> bottomPrimaryDraw
            else -> null
        }

        currentPrimaryActionButton?.applyColorFilter(getAdjustedPrimaryColor())
        bottomEditorFilterActions.beVisibleIf(currPrimaryAction == PRIMARY_ACTION_FILTER)
        bottomEditorCropRotateActions.beVisibleIf(currPrimaryAction == PRIMARY_ACTION_CROP_ROTATE)
        bottomEditorDrawActions.beVisibleIf(currPrimaryAction == PRIMARY_ACTION_DRAW)

        if (currPrimaryAction == PRIMARY_ACTION_FILTER && bottomActionsFilterList.adapter == null) {
            Thread {
                val thumbnailSize =
                    resources.getDimension(R.dimen.bottom_filters_thumbnail_size).toInt()
                val bitmap = Glide.with(this)
                    .asBitmap()
                    .load(uri).listener(object : RequestListener<Bitmap> {
                        override fun onLoadFailed(
                            e: GlideException?,
                            model: Any?,
                            target: Target<Bitmap>?,
                            isFirstResource: Boolean,
                        ): Boolean {
                            showErrorToast(e.toString())
                            return false
                        }

                        override fun onResourceReady(
                            resource: Bitmap?,
                            model: Any?,
                            target: Target<Bitmap>?,
                            dataSource: DataSource?,
                            isFirstResource: Boolean,
                        ) = false
                    })
                    .submit(thumbnailSize, thumbnailSize)
                    .get()

                runOnUiThread {
                    val filterThumbnailsManager = FilterThumbnailsManager()
                    filterThumbnailsManager.clearThumbs()

                    val noFilter = Filter(getString(R.string.none))
                    filterThumbnailsManager.addThumb(FilterItem(bitmap, noFilter))

                    FilterPack.getFilterPack(this).forEach {
                        val filterItem = FilterItem(bitmap, it)
                        filterThumbnailsManager.addThumb(filterItem)
                    }

                    val filterItems = filterThumbnailsManager.processThumbs()
                    val adapter = FiltersAdapter(applicationContext, filterItems) {
                        val layoutManager =
                            bottomActionsFilterList.layoutManager as LinearLayoutManager
                        applyFilter(filterItems[it])

                        if (it == layoutManager.findLastCompletelyVisibleItemPosition() || it == layoutManager.findLastVisibleItemPosition()) {
                            bottomActionsFilterList.smoothScrollBy(thumbnailSize, 0)
                        } else if (it == layoutManager.findFirstCompletelyVisibleItemPosition() || it == layoutManager.findFirstVisibleItemPosition()) {
                            bottomActionsFilterList.smoothScrollBy(-thumbnailSize, 0)
                        }
                    }

                    bottomActionsFilterList.adapter = adapter
                    adapter.notifyDataSetChanged()
                }
            }.start()
        }

        if (currPrimaryAction != PRIMARY_ACTION_CROP_ROTATE) {
            bottomAspectRatios.beGone()
            currCropRotateAction = CROP_ROTATE_NONE
            updateCropRotateActionButtons()
        }
    }

    private fun applyFilter(filterItem: FilterItem) {
        val newBitmap = filterInitialBitmap?.let { Bitmap.createBitmap(it) }
        defaultImageView.setImageBitmap(filterItem.filter.processFilter(newBitmap))
    }

    private fun updateAspectRatio(aspectRatio: Int) {
        currAspectRatio = aspectRatio
        config.lastEditorCropAspectRatio = aspectRatio
        updateAspectRatioButtons()

        cropImageView.apply {
            if (aspectRatio == ASPECT_RATIO_FREE) {
                setFixedAspectRatio(false)
            } else {
                val newAspectRatio = when (aspectRatio) {
                    ASPECT_RATIO_ONE_ONE -> Pair(1, 1)
                    ASPECT_RATIO_FOUR_THREE -> Pair(4, 3)
                    ASPECT_RATIO_SIXTEEN_NINE -> Pair(16, 9)
                    else -> Pair(lastOtherAspectRatio!!.first, lastOtherAspectRatio!!.second)
                }
                try {
                    setAspectRatio(newAspectRatio.first, newAspectRatio.second)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun updateAspectRatioButtons() {
        arrayOf(
            bottomAspectRatioFree,
            bottomAspectRatioOneOne,
            bottomAspectRatioFourThree,
            bottomAspectRatioSixteenNine,
            bottomAspectRatioOther
        ).forEach {
            it.setTextColor(Color.WHITE)
        }

        val currentAspectRatioButton = when (currAspectRatio) {
            ASPECT_RATIO_FREE -> bottomAspectRatioFree
            ASPECT_RATIO_ONE_ONE -> bottomAspectRatioOneOne
            ASPECT_RATIO_FOUR_THREE -> bottomAspectRatioFourThree
            ASPECT_RATIO_SIXTEEN_NINE -> bottomAspectRatioSixteenNine
            else -> bottomAspectRatioOther
        }

        currentAspectRatioButton.setTextColor(getAdjustedPrimaryColor())
    }

    private fun updateCropRotateActionButtons() {
        arrayOf(bottomAspectRatio).forEach {
            it.applyColorFilter(Color.WHITE)
        }

        val primaryActionView = when (currCropRotateAction) {
            CROP_ROTATE_ASPECT_RATIO -> bottomAspectRatio
            else -> null
        }

        primaryActionView?.applyColorFilter(getAdjustedPrimaryColor())
    }

    private fun updateDrawColor(color: Int) {
        drawColor = color
        bottomDrawColor.applyColorFilter(color)
        config.lastEditorDrawColor = color
        editorDrawCanvas.updateColor(color)
    }

    private fun resizeImage() {
        val point = getAreaSize()
        if (point == null) {
            toast(R.string.unknown_error_occurred)
            return
        }

        ResizeDialog(this, point) {
            resizeWidth = it.x
            resizeHeight = it.y
            cropImageView.getCroppedImageAsync()
        }
    }

    private fun shouldCropSquare(): Boolean {
        val extras = intent.extras
        return if (extras != null && extras.containsKey(ASPECT_X) && extras.containsKey(ASPECT_Y)) {
            extras.getInt(ASPECT_X) == extras.getInt(ASPECT_Y)
        } else {
            false
        }
    }

    private fun getAreaSize(): Point? {
        val rect = cropImageView.cropRect ?: return null
        val rotation = cropImageView.rotatedDegrees
        return if (rotation == 0 || rotation == 180) {
            Point(rect.width(), rect.height())
        } else {
            Point(rect.height(), rect.width())
        }
    }

    override fun onCropImageComplete(view: CropImageView, result: CropImageView.CropResult) {
        if (result.error == null) {
            val bitmap = result.bitmap
            if (isSharingBitmap) {
                isSharingBitmap = false
                shareBitmap(bitmap)
                return
            }

            if (isCropIntent) {
                if (saveUri.scheme == "file") {
                    saveUri.path?.let { saveBitmapToFile(bitmap, it, true) }
                } else {
                    var inputStream: InputStream? = null
                    var outputStream: OutputStream? = null
                    try {
                        val stream = ByteArrayOutputStream()
                        bitmap.compress(CompressFormat.JPEG, 100, stream)
                        inputStream = ByteArrayInputStream(stream.toByteArray())
                        outputStream = contentResolver.openOutputStream(saveUri)
                        if (outputStream != null) {
                            inputStream.copyTo(outputStream)
                        }
                    } finally {
                        inputStream?.close()
                        outputStream?.close()
                    }

                    Intent().apply {
                        data = saveUri
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        setResult(RESULT_OK, this)
                    }
                    finish()
                }
            } else if (saveUri.scheme == "file") {
                saveUri.path?.let {
                    SaveAsDialog(this, it, true) {
                        saveBitmapToFile(bitmap, it, true)
                    }
                }
            } else if (saveUri.scheme == "content") {
                val filePathGetter = getNewFilePath()
                SaveAsDialog(this, filePathGetter.first, filePathGetter.second) {
                    saveBitmapToFile(bitmap, it, true)
                }
            } else {
                toast(R.string.unknown_file_location)
            }
        } else {
            toast("${getString(R.string.image_editing_failed)}: ${result.error.message}")
        }
    }

    private fun getNewFilePath(): Pair<String, Boolean> {
        var newPath = applicationContext.getRealPathFromURI(saveUri) ?: ""
        if (newPath.startsWith("/mnt/")) {
            newPath = ""
        }

        var shouldAppendFilename = true
        if (newPath.isEmpty()) {
            val filename = applicationContext.getFilenameFromContentUri(saveUri) ?: ""
            if (filename.isNotEmpty()) {
                val path =
                    if (intent.extras?.containsKey(REAL_FILE_PATH) == true) intent.getStringExtra(
                        REAL_FILE_PATH
                    )?.getParentPath() else internalStoragePath
                newPath = "$path/$filename"
                shouldAppendFilename = false
            }
        }

        if (newPath.isEmpty()) {
            newPath = "$internalStoragePath/${getCurrentFormattedDateTime()}.${
                saveUri.toString().getFilenameExtension()
            }"
            shouldAppendFilename = false
        }

        return Pair(newPath, shouldAppendFilename)
    }

    private fun saveBitmapToFile(bitmap: Bitmap, path: String, showSavingToast: Boolean) {
        try {
            Thread {
                val file = File(path)
                val fileDirItem = FileDirItem(path, path.getFilenameFromPath())
                getFileOutputStream(fileDirItem, true) {
                    if (it != null) {
                        saveBitmap(file, bitmap, it, showSavingToast)
                    } else {
                        toast(R.string.image_editing_failed)
                    }
                }
            }.start()
        } catch (e: Exception) {
            showErrorToast(e)
        } catch (e: OutOfMemoryError) {
            toast(R.string.out_of_memory_error)
        }
    }

    @TargetApi(Build.VERSION_CODES.N)
    private fun saveBitmap(
        file: File,
        bitmap: Bitmap,
        out: OutputStream,
        showSavingToast: Boolean,
    ) {
        if (showSavingToast) {
            toast(R.string.saving)
        }

        if (resizeWidth > 0 && resizeHeight > 0) {
            val resized = Bitmap.createScaledBitmap(bitmap, resizeWidth, resizeHeight, false)
            resized.compress(file.absolutePath.getCompressionFormat(), 90, out)
        } else {
            bitmap.compress(file.absolutePath.getCompressionFormat(), 90, out)
        }

        try {
            if (isNougatPlus()) {
                val newExif = ExifInterface(file.absolutePath)
                oldExif?.copyTo(newExif, false)
            }
        } catch (e: Exception) {
        }

        setResult(Activity.RESULT_OK, intent)
        scanFinalPath(file.absolutePath)
        out.close()
    }

    private fun editWith() {
        openEditor(uri.toString(), true)
        isEditingWithThirdParty = true
    }

    private fun scanFinalPath(path: String) {
        scanPathRecursively(path) {
            setResult(Activity.RESULT_OK, intent)
            toast(R.string.file_saved)
            finish()
        }
    }
}
