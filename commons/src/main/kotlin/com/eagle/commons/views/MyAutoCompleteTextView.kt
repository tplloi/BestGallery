package com.eagle.commons.views

import android.content.Context
import android.util.AttributeSet
import com.eagle.commons.ext.adjustAlpha
import com.eagle.commons.ext.applyColorFilter

class MyAutoCompleteTextView : androidx.appcompat.widget.AppCompatAutoCompleteTextView {
    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    )

    fun setColors(textColor: Int, accentColor: Int, backgroundColor: Int) {
        background?.mutate()?.applyColorFilter(accentColor)

        // requires android:textCursorDrawable="@null" in xml to color the cursor too
        setTextColor(textColor)
        setHintTextColor(textColor.adjustAlpha(0.5f))
        setLinkTextColor(accentColor)
    }
}
