package top.yooho.search

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.RadioGroup
import androidx.core.graphics.drawable.toDrawable
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import mozilla.components.browser.state.search.SearchEngine
import mozilla.components.browser.state.state.searchEngines
import mozilla.components.browser.state.state.selectedOrDefaultSearchEngine
import org.mozilla.reference.browser.ext.components
import top.yooho.browser.R
import kotlin.coroutines.CoroutineContext
import kotlin.io.encoding.Base64

abstract class SearchEngineListPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : Preference(context, attrs, defStyleAttr), CoroutineScope {

    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main
    protected var searchEngines: List<SearchEngine> = emptyList()
    protected var searchEngineGroup: RadioGroup? = null

    protected abstract val itemResId: Int

    init {
        layoutResource = R.layout.preference_search_engine_chooser
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        searchEngineGroup = holder.itemView.findViewById(R.id.search_engine_group)
        val context = searchEngineGroup!!.context

        searchEngines = loadSearchEngines()

        refreshSearchEngineViews(context)
    }

    override fun onDetached() {
        job.cancel()
        super.onDetached()
    }

    protected abstract fun updateDefaultItem(defaultButton: CompoundButton)

    fun refetchSearchEngines() {
        searchEngines = loadSearchEngines()
        refreshSearchEngineViews(this@SearchEngineListPreference.context)
    }

    private fun refreshSearchEngineViews(context: Context) {
        if (searchEngineGroup == null) {
            // We want to refresh the search engine list of this preference in onResume,
            // but the first time this preference is created onResume is called before onCreateView
            // so searchEngineGroup is not set yet.
            return
        }

        val defaultSearchEngine =
            context.components.core.store.state.search.selectedOrDefaultSearchEngine?.id

        searchEngineGroup!!.removeAllViews()

        val layoutInflater = LayoutInflater.from(context)
        val layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )

        for (i in searchEngines.indices) {
            val engine = searchEngines[i]
            val engineId = engine.id
            val engineItem = makeButtonFromSearchEngine(engine, layoutInflater, context.resources)
            engineItem.id = i
            engineItem.tag = engineId
            if (engineId == defaultSearchEngine) {
                updateDefaultItem(engineItem)
            }
            searchEngineGroup!!.addView(engineItem, layoutParams)
        }
    }

    private fun makeButtonFromSearchEngine(
        engine: SearchEngine,
        layoutInflater: LayoutInflater,
        res: Resources,
    ): CompoundButton {
        val buttonItem = layoutInflater.inflate(itemResId, null) as CompoundButton
        buttonItem.text = engine.name
        val iconSize = res.getDimension(R.dimen.preference_icon_drawable_size).toInt()
        val engineIcon = engine.icon.toDrawable(res)
        engineIcon.setBounds(0, 0, iconSize, iconSize)
        val drawables = buttonItem.compoundDrawables
        buttonItem.setCompoundDrawablesRelative(engineIcon, null, drawables[2], null)
        return buttonItem
    }

    //loamen 获取引擎列表
    private fun loadSearchEngines(): List<SearchEngine> {

        //判断searchEngines是否包含mita,如果不包含则添加
        if (!context.components.core.store.state.search.searchEngines.any { it.id == "mita" }) {
            context.components.useCases.searchUseCases.addSearchEngine(
                SearchEngine(
                    "mita",
                    "秘塔AI",
                    BitmapFactory.decodeResource(context.resources, R.drawable.ic_mita),
                    "UTF-8",
                    SearchEngine.Type.CUSTOM,
                    listOf("https://metaso.cn/?q={searchTerms}"),
                    "https://metaso.cn",
                ),
            )
        }

        if (!context.components.core.store.state.search.searchEngines.any { it.id == "baidu" }) {
            context.components.useCases.searchUseCases.addSearchEngine(
                SearchEngine(
                    "baidu",
                    "百度",
                    BitmapFactory.decodeResource(context.resources, R.drawable.ic_baidu),
                    "UTF-8",
                    SearchEngine.Type.CUSTOM,
                    listOf("https://m.baidu.com/s?word={searchTerms}"),
                    "https://m.baidu.com/su?wd={searchTerms}&action=opensearch&ie=UTF-8",
                ),
            )
        }

        if (!context.components.core.store.state.search.searchEngines.any { it.id == "360" }) {
            context.components.useCases.searchUseCases.addSearchEngine(
                SearchEngine(
                    "360",
                    "360",
                    BitmapFactory.decodeResource(context.resources, R.drawable.ic_360),
                    "UTF-8",
                    SearchEngine.Type.CUSTOM,
                    listOf("https://m.so.com/s?q={searchTerms}"),
                    "https://sug.so.360.cn/suggest?encodein=utf-8&encodeout=utf-8&word={searchTerms}",
                ),
            )
        }

        if (!context.components.core.store.state.search.searchEngines.any { it.id == "sogou" }) {
            context.components.useCases.searchUseCases.addSearchEngine(
                SearchEngine(
                    "sogou",
                    "搜狗",
                    BitmapFactory.decodeResource(context.resources, R.drawable.ic_sogou),
                    "UTF-8",
                    SearchEngine.Type.CUSTOM,
                    listOf("https://m.sogou.com/web/searchList.jsp?keyword={searchTerms}"),
                    "https://m.sogou.com/sugg/ajaj?type=web&query={searchTerms}",
                ),
            )
        }

        if (!context.components.core.store.state.search.searchEngines.any { it.id == "sm" }) {
            context.components.useCases.searchUseCases.addSearchEngine(
                SearchEngine(
                    "sm",
                    "神马",
                    BitmapFactory.decodeResource(context.resources, R.drawable.ic_sm),
                    "UTF-8",
                    SearchEngine.Type.CUSTOM,
                    listOf("https://m.sm.cn/s?q={searchTerms}"),
                    "https://sug.sm.cn/s?enc=utf-8&wd={searchTerms}",
                ),
            )
        }

        if (!context.components.core.store.state.search.searchEngines.any { it.id == "quark" }) {
            context.components.useCases.searchUseCases.addSearchEngine(
                SearchEngine(
                    "quark",
                    "夸克",
                    BitmapFactory.decodeResource(context.resources, R.drawable.ic_quark),
                    "UTF-8",
                    SearchEngine.Type.CUSTOM,
                    listOf("https://quark.sm.cn/s?q={searchTerms}"),
                    "https://sug.sm.cn/s?enc=utf-8&wd={searchTerms}",
                ),
            )
        }

        if (!context.components.core.store.state.search.searchEngines.any { it.id == "douyin" }) {
            context.components.useCases.searchUseCases.addSearchEngine(
                SearchEngine(
                    "douyin",
                    "抖音",
                    BitmapFactory.decodeResource(context.resources, R.drawable.ic_douyin),
                    "UTF-8",
                    SearchEngine.Type.CUSTOM,
                    listOf("https://www.douyin.com/search/{searchTerms}"),
                    "https://www.douyin.com/aweme/v1/search/sug/?keyword={searchTerms}",
                ),
            )
        }

        if (!context.components.core.store.state.search.searchEngines.any { it.id == "toutiao" }) {
            context.components.useCases.searchUseCases.addSearchEngine(
                SearchEngine(
                    "toutiao",
                    "头条",
                    BitmapFactory.decodeResource(context.resources, R.drawable.ic_toutiao),
                    "UTF-8",
                    SearchEngine.Type.CUSTOM,
                    listOf("https://m.toutiao.com/search/?keyword={searchTerms}"),
                    "https://m.toutiao.com/search/sug/?keyword={searchTerms}",
                ),
            )
        }

        return context.components.core.store.state.search.searchEngines
    }
}

