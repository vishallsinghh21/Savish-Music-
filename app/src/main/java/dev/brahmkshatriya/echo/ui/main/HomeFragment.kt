package dev.brahmkshatriya.echo.ui.main

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import com.google.android.material.transition.MaterialSharedAxis
import dev.brahmkshatriya.echo.R
import dev.brahmkshatriya.echo.common.clients.HomeFeedClient
import dev.brahmkshatriya.echo.common.models.ExtensionType
import dev.brahmkshatriya.echo.common.models.Feed
import dev.brahmkshatriya.echo.common.models.Feed.Buttons.Companion.EMPTY
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.databinding.FragmentHomeBinding
import dev.brahmkshatriya.echo.extensions.ExtensionLoader
import dev.brahmkshatriya.echo.extensions.ExtensionUtils.getAs
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
import dev.brahmkshatriya.echo.utils.ContextUtils.dpToPx
import dev.brahmkshatriya.echo.utils.ContextUtils.observe
import dev.brahmkshatriya.echo.utils.ui.AnimationUtils.setupTransition
import kotlinx.coroutines.flow.combine
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

class HomeFragment : Fragment(R.layout.fragment_home) {

    private val extensionLoader by inject<ExtensionLoader>()

    private val feedData by lazy {
        val vm by viewModel<FeedViewModel>()
        val id = "home"
        vm.getFeedData(id, EMPTY, cached = {
            val curr = current.value!!
            val feed = Cached.getFeedShelf(app, curr.id, id).getOrThrow()
            FeedData.State(curr.id, null, feed)
        }) {
            val curr = current.value!!
            val feed = Cached.savingFeed(
                app, curr, id,
                curr.getAs<HomeFeedClient, Feed<Shelf>> { loadHomeFeed() }.getOrThrow()
            )
            FeedData.State(curr.id, null, feed)
        }
    }

    private val listener by lazy { getFeedListener(requireParentFragment()) }
    private val feedAdapter by lazy { getFeedAdapter(feedData, listener) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentHomeBinding.bind(view)
        setupTransition(view, false, MaterialSharedAxis.Y)
        applyInsets(binding.recyclerView, binding.appBarOutline) {
            binding.swipeRefresh.configure(it)
        }
        val uiViewModel by activityViewModel<UiViewModel>()
        
        observe(uiViewModel.navigationReselected) {
            if (it != 0) return@observe
            ExtensionsListBottomSheet.newInstance(ExtensionType.MUSIC)
                .show(parentFragmentManager, null)
        }
        observe(
            uiViewModel.navigation.combine(feedData.backgroundImageFlow) { a, b -> a to b }
        ) { (curr, bg) ->
            if (curr != 0) return@observe
            uiViewModel.currentNavBackground.value = bg
        }
        applyBackPressCallback()
        getTouchHelper(listener).attachToRecyclerView(binding.recyclerView)

        // Seedhe clean Feed RecyclerView load karna (Empty Unified box detached)
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

        buildDynamicCapsules(binding)
    }

    private fun buildDynamicCapsules(binding: FragmentHomeBinding) {
        val context = requireContext()
        val container = binding.layoutCapsulesContainer
        container.removeAllViews()

        data class PlatformItem(val id: String?, val name: String, val colorHex: String)

        // Auto brand color mapping
        fun getBrandColor(name: String): String {
            val lower = name.lowercase()
            return when {
                "spotify" in lower -> "#1DB954"
                "youtube" in lower -> "#FF0033"
                "saavn" in lower -> "#00D2C4"
                "deezer" in lower -> "#A238FF"
                "apple" in lower -> "#FC3C44"
                "offline" in lower -> "#FF9900"
                "soundcloud" in lower -> "#FF5500"
                else -> "#00E5FF"
            }
        }

        val platforms = mutableListOf<PlatformItem>()
        platforms.add(PlatformItem(null, "All media", "#00E5FF"))
        platforms.add(PlatformItem("offline", "Offline", "#FF9900"))

        // Registered extensions se dynamic platform names & colors auto fetch karna
        try {
            extensionLoader.loadedExtensions.value.forEach { ext ->
                platforms.add(PlatformItem(ext.id, ext.name, getBrandColor(ext.name)))
            }
        } catch (_: Exception) {
            platforms.add(PlatformItem("youtube", "YouTube", "#FF0033"))
            platforms.add(PlatformItem("spotify", "Spotify", "#1DB954"))
            platforms.add(PlatformItem("jiosaavn", "JioSaavn", "#00D2C4"))
            platforms.add(PlatformItem("deezer", "Deezer", "#A238FF"))
        }

        val capsuleViews = mutableListOf<Pair<MaterialCardView, TextView>>()

        fun applyPlatformSelection(selectedCard: MaterialCardView, selectedTv: TextView, item: PlatformItem) {
            val color = Color.parseColor(item.colorHex)
            val unselectedStroke = Color.parseColor("#27333F")
            val unselectedBg = Color.parseColor("#141B22")
            val unselectedText = Color.parseColor("#9BA8B5")

            capsuleViews.forEach { (card, tv) ->
                val isCurrent = card == selectedCard
                if (isCurrent) {
                    card.strokeColor = color
                    card.strokeWidth = context.dpToPx(2.5f)
                    card.setCardBackgroundColor(ColorStateList.valueOf(Color.parseColor("#1C2632")))
                    tv.setTextColor(color)
                    tv.typeface = Typeface.DEFAULT_BOLD
                } else {
                    card.strokeColor = unselectedStroke
                    card.strokeWidth = context.dpToPx(1f)
                    card.setCardBackgroundColor(ColorStateList.valueOf(unselectedBg))
                    tv.setTextColor(unselectedText)
                    tv.typeface = Typeface.DEFAULT
                }
            }

            binding.viewAmbientGlow.setBackgroundColor(color)
            binding.searchBarContainer.strokeColor = color
            binding.ivSearchIcon.imageTintList = ColorStateList.valueOf(color)
            binding.etHomeSearch.hint = "Search songs in Savish ${item.name}..."

            feedData.refresh()
        }

        platforms.forEachIndexed { index, item ->
            val card = MaterialCardView(context).apply {
                radius = context.dpToPx(24f).toFloat()
                strokeWidth = context.dpToPx(1f)
                strokeColor = Color.parseColor("#27333F")
                setCardBackgroundColor(ColorStateList.valueOf(Color.parseColor("#141B22")))
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    context.dpToPx(48f)
                ).apply {
                    marginEnd = context.dpToPx(10f)
                }
                layoutParams = params
            }

            val innerLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(context.dpToPx(16f), 0, context.dpToPx(16f), 0)
            }

            val dot = View(context).apply {
                val dotParams = LinearLayout.LayoutParams(
                    context.dpToPx(8f),
                    context.dpToPx(8f)
                ).apply {
                    marginEnd = context.dpToPx(8f)
                }
                layoutParams = dotParams
                background = ContextCompat.getDrawable(context, android.R.drawable.presence_online)
                backgroundTintList = ColorStateList.valueOf(Color.parseColor(item.colorHex))
            }

            val tv = TextView(context).apply {
                text = item.name
                textSize = 15f
                setTextColor(Color.parseColor("#9BA8B5"))
            }

            innerLayout.addView(dot)
            innerLayout.addView(tv)
            card.addView(innerLayout)

            capsuleViews.add(card to tv)

            card.setOnClickListener {
                applyPlatformSelection(card, tv, item)
            }

            container.addView(card)

            if (index == 0) {
                applyPlatformSelection(card, tv, item)
            }
        }
    }
}
