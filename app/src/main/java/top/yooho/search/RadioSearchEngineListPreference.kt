package top.yooho.search

import android.content.Context
import android.util.AttributeSet
import android.widget.CompoundButton
import android.widget.RadioGroup
import androidx.preference.PreferenceViewHolder
import org.mozilla.reference.browser.ext.components
import top.yooho.browser.R

class RadioSearchEngineListPreference : SearchEngineListPreference, RadioGroup.OnCheckedChangeListener {

    override val itemResId: Int
        get() = R.layout.search_engine_radio_button

    @Suppress("unused")
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    @Suppress("unused")
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        searchEngineGroup!!.setOnCheckedChangeListener(this)
    }

    override fun updateDefaultItem(defaultButton: CompoundButton) {
        defaultButton.isChecked = true
    }

    override fun onCheckedChanged(group: RadioGroup, checkedId: Int) {
        val selectedEngine = group.getChildAt(checkedId)

        // check if the corresponding button was pressed or a11y focused.
        val hasProperState = selectedEngine.isPressed || selectedEngine.isAccessibilityFocused

        /* onCheckedChanged is called intermittently before the search engine table is full, so we
           must check these conditions to prevent crashes and inconsistent states. */
        if (group.childCount != searchEngines.count() || selectedEngine == null || !hasProperState) {
            return
        }

        val newDefaultEngine = searchEngines[checkedId]

        context.components.useCases.searchUseCases.selectSearchEngine(newDefaultEngine)
    }
}
