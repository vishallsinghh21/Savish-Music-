package dev.brahmkshatriya.echo.ui.main.search

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.transition.MaterialSharedAxis
import dev.brahmkshatriya.echo.R
import dev.brahmkshatriya.echo.common.clients.SearchFeedClient
import dev.brahmkshatriya.echo.common.models.ExtensionType
import dev.brahmkshatriya.echo.common.models.Feed
import dev.brahmkshatriya.echo.common.models.Feed.Buttons.Companion.EMPTY
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.databinding.FragmentSearchBinding
import dev.brahmkshatriya.echo.extensions.ExtensionUtils.getAs
import dev.brahmkshatriya.echo.extensions.ExtensionUtils.getExtension
import dev.brahmkshatriya.echo.extensions.cache.Cached
import dev.brahmkshatriya.echo.ui.common.GridAdapter.Companion.configureGridLayout
import dev.brahmkshatriya.echo.ui.common.UiViewModel
import dev.brahmkshatriya.echo.ui.common.UiViewModel.Companion.applyBackPressCallback
import dev.brahmkshatriya.echo.ui.common.UiViewModel.Companion.configure
import dev.brahmkshatriya.echo.ui.extensions.list.ExtensionsListBottomSheet
import dev.brahmkshatriya.echo.ui.feed.FeedAdapter.Companion.getFeedAdapter
import dev.brahmkshatriya.echo.ui.feed.FeedAdapter.Companion.getTouchHelper
import dev.brahmkshatriya.echo.ui.feed.FeedClickListener.Companion.getFeedListener
import dev.brahmkshatriya.echo.ui.feed.FeedData
import dev.brahmkshatriya.echo.ui.feed.FeedViewModel
import dev.brahmkshatriya.echo.ui.main.MainFragment.Companion.applyInsets
import dev.brahmkshatriya.echo.ui.main.search.SearchViewModel.Companion.saveInHistory
import dev.brahmkshatriya.echo.utils.ContextUtils.observe
import dev.brahmkshatriya.echo.utils.ui.AnimationUtils.setupTransition
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

class SearchFragment : Fragment(R.layout.fragment_search) {

    private val argId by lazy { arguments?.getString("extensionId") }
    private val searchViewModel by activityViewModel<SearchViewModel>()
    private var extensionId = ""

    private val feedData by lazy {
        val vm by viewModel<FeedViewModel>()
        val id = "search"
        vm.getFeedData(
            id,
            EMPTY,
            false,
            searchViewModel.queryFlow,
            cached = {
                val curr = music.getExtension(argId) ?: current.value ?: return@getFeedData null
                val query = searchViewModel.queryFlow.value
                val feed = Cached.getFeedShelf(app, curr.id, "$id-$query").getOrNull()
                feed?.let { FeedData.State(curr.id, null, it) }
            }
        ) {
            val curr = music.getExtension(argId) ?: current.value ?: return@getFeedData null
            val query = searchViewModel.queryFlow.value
            curr.saveInHistory(vm.app.context, query)
            val feed = Cached.savingFeed(
                app, curr, "$id-$query",
                curr.getAs<SearchFeedClient, Feed<Shelf>> { loadSearchFeed(query) }.getOrThrow()
            )
            extensionId = curr.id
            FeedData.State(curr.id, null, feed)
        }
    }

    private val listener by lazy {
        getFeedListener(if (argId == null) requireParentFragment() else this)
    }

    private val feedAdapter by lazy {
        getFeedAdapter(feedData, listener)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentSearchBinding.bind(view)
        setupTransition(view, false, MaterialSharedAxis.Y)
        applyInsets(binding.recyclerView, binding.appBarOutline) {
            binding.swipeRefresh.configure(it)
        }
        val uiViewModel by activityViewModel<UiViewModel>()

        observe(uiViewModel.navigationReselected) {
            if (it != 2) return@observe
            binding.etSearchInput.requestFocus()
        }

        observe(
            uiViewModel.navigation.combine(feedData.backgroundImageFlow) { a, b -> a to b }
        ) { (curr, bg) ->
            if (curr != 2) return@observe
            uiViewModel.currentNavBackground.value = bg
        }

        applyBackPressCallback()
        getTouchHelper(listener).attachToRecyclerView(binding.recyclerView)

        configureGridLayout(
            binding.recyclerView,
            feedAdapter.withLoading(this)
        )

        binding.swipeRefresh.run {
            setOnRefreshListener { feedData.refresh() }
            observe(feedData.isRefreshingFlow) {
                isRefreshing = it
            }
        }

        // Settings Sheet
        binding.btnSearchSettings.setOnClickListener {
            ExtensionsListBottomSheet.newInstance(ExtensionType.MUSIC)
                .show(parentFragmentManager, null)
        }

        // Auto sync query when coming from Home
        viewLifecycleOwner.lifecycleScope.launch {
            searchViewModel.queryFlow.collectLatest { query ->
                if (query.isNotEmpty() && binding.etSearchInput.text.toString() != query) {
                    binding.etSearchInput.setText(query)
                    binding.etSearchInput.setSelection(query.length)
                    feedData.refresh()
                }
            }
        }

        // Dynamic theme & aura updates with active platform
        viewLifecycleOwner.lifecycleScope.launch {
            feedData.current.collectLatest { ext ->
                val activeName = ext?.name ?: "All media"
                val activeColor = when {
                    "spotify" in activeName.lowercase() -> Color.parseColor("#1DB954")
                    "youtube" in activeName.lowercase() -> Color.parseColor("#FF0033")
                    "saavn" in activeName.lowercase() -> Color.parseColor("#00D2C4")
                    "deezer" in activeName.lowercase() -> Color.parseColor("#A238FF")
                    "offline" in activeName.lowercase() -> Color.parseColor("#FFA500")
                    else -> Color.parseColor("#00E5FF")
                }

                val dm = resources.displayMetrics
                val radius = dm.widthPixels * 0.95f
                val radialGradient = GradientDrawable().apply {
                    gradientType = GradientDrawable.RADIAL_GRADIENT
                    gradientRadius = radius
                    setGradientCenter(0.5f, 0.20f)
                    colors = intArrayOf(
                        ColorUtils.setAlphaComponent(activeColor, 110),
                        ColorUtils.setAlphaComponent(activeColor, 35),
                        Color.parseColor("#070A0F")
                    )
                }
                binding.viewSearchAmbientGlow.background = radialGradient
                binding.searchBarContainer.strokeColor = activeColor
                binding.ivSearchInputIcon.imageTintList = ColorStateList.valueOf(activeColor)

                binding.etSearchInput.hint = "Search songs in Savish $activeName..."
            }
        }

        // Trigger search on Enter
        binding.etSearchInput.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = binding.etSearchInput.text?.toString()?.trim().orEmpty()
                val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(v.windowToken, 0)
                searchViewModel.queryFlow.value = query
                feedData.refresh()
                true
            } else false
        }
    }
}
