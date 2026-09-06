package dev.brahmkshatriya.echo.ui.main

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.core.graphics.ColorUtils
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
import dev.brahmkshatriya.echo.ui.player.PlayerViewModel
import dev.brahmkshatriya.echo.utils.ContextUtils.observe
import dev.brahmkshatriya.echo.utils.ui.AnimationUtils.setupTransition
import kotlinx.coroutines.flow.combine
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

class HomeFragment : Fragment(R.layout.fragment_home) {

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

    private val playerViewModel by activityViewModel<PlayerViewModel>()
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

        // Bypassing empty Unified Extension container & Header items
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

        // Bottom playback button triggers
        binding.btnPlayAction.setOnClickListener {
            playerViewModel.playPause()
        }

        setupPlatformSelection(binding)
    }

    private fun setupPlatformSelection(binding: FragmentHomeBinding) {
        data class PlatformEntry(
            val id: String,
            val card: MaterialCardView,
            val dot: View,
            val tv: TextView,
            val brandColorHex: String,
            val name: String
        )

        val entries = listOf(
            PlatformEntry("all", binding.capsuleAllMedia, binding.dotAllMedia, binding.tvAllMedia, "#00E5FF", "All media"),
            PlatformEntry("offline", binding.capsuleOffline, binding.dotOffline, binding.tvOffline, "#FF9900", "Offline"),
            PlatformEntry("deezer", binding.capsuleDeezer, binding.dotDeezer, binding.tvDeezer, "#A238FF", "Deezer"),
            PlatformEntry("youtube", binding.capsuleYouTube, binding.dotYouTube, binding.tvYouTube, "#FF0033", "YouTube"),
            PlatformEntry("spotify", binding.capsuleSpotify, binding.dotSpotify, binding.tvSpotify, "#1DB954", "Spotify"),
            PlatformEntry("jiosaavn", binding.capsuleJioSaavn, binding.dotJioSaavn, binding.tvJioSaavn, "#00D2C4", "JioSaavn")
        )

        // Permanent brand colored dots
        entries.forEach { entry ->
            val color = Color.parseColor(entry.brandColorHex)
            entry.dot.backgroundTintList = ColorStateList.valueOf(color)
            entry.tv.setTextColor(Color.WHITE) // Text hamesha Pure White rahega
        }

        fun updateDiamondAura(activeColor: Int) {
            val dm = resources.displayMetrics
            val radius = dm.widthPixels * 0.95f

            val radialGradient = GradientDrawable().apply {
                gradientType = GradientDrawable.RADIAL_GRADIENT
                gradientRadius = radius
                setGradientCenter(0.5f, 0.32f)
                colors = intArrayOf(
                    ColorUtils.setAlphaComponent(activeColor, 120),
                    ColorUtils.setAlphaComponent(activeColor, 45),
                    Color.parseColor("#070A0F")
                )
            }
            binding.viewAmbientGlow.background = radialGradient
        }

        fun select(target: PlatformEntry, triggerBackend: Boolean = true) {
            val activeColor = Color.parseColor(target.brandColorHex)
            val strokeOff = Color.parseColor("#25313D")
            val bgOff = Color.parseColor("#141B22")

            entries.forEach { entry ->
                val isSelected = (entry == target)
                if (isSelected) {
                    entry.card.strokeColor = activeColor
                    entry.card.strokeWidth = 6
                    entry.card.setCardBackgroundColor(ColorStateList.valueOf(Color.parseColor("#1C2632")))
                    entry.tv.typeface = Typeface.DEFAULT_BOLD
                } else {
                    entry.card.strokeColor = strokeOff
                    entry.card.strokeWidth = 2
                    entry.card.setCardBackgroundColor(ColorStateList.valueOf(bgOff))
                    entry.tv.typeface = Typeface.DEFAULT
                }
            }

            updateDiamondAura(activeColor)

            binding.searchBarContainer.strokeColor = activeColor
            binding.ivSearchIcon.imageTintList = ColorStateList.valueOf(activeColor)
            binding.etHomeSearch.hint = "Search songs in Savish ${target.name}..."

            if (triggerBackend) {
                // Switch backend extension source to match selected capsule
                feedData.current.value?.let { curr ->
                    if (target.id != "all") {
                        // Switch if extension matches
                    }
                }
                feedData.refresh()
            }
        }

        entries.forEach { entry ->
            entry.card.setOnClickListener { select(entry) }
        }

        // Default: All media active
        select(entries[0], triggerBackend = false)

        // Two-way sync: jab extension sheet se platform chunein, toh capsule auto switch ho jaye
        observe(feedData.current) { currentExt ->
            val extName = currentExt?.name?.lowercase() ?: "all"
            val matched = entries.find { entry ->
                when (entry.id) {
                    "all" -> extName.contains("unified") || extName.contains("all")
                    else -> extName.contains(entry.id)
                }
            } ?: entries[0]
            select(matched, triggerBackend = false)
        }
    }
}
