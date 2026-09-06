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

        // Clean Feed: Direct Albums, Liked Music, Speed Dial
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

        setupPlatformSelection(binding)
    }

    private fun setupPlatformSelection(binding: FragmentHomeBinding) {
        data class PlatformEntry(
            val card: MaterialCardView,
            val tv: TextView,
            val colorHex: String,
            val name: String
        )

        val entries = listOf(
            PlatformEntry(binding.capsuleAllMedia, binding.tvAllMedia, "#00E5FF", "All media"),
            PlatformEntry(binding.capsuleOffline, binding.tvOffline, "#FF9900", "Offline"),
            PlatformEntry(binding.capsuleDeezer, binding.tvDeezer, "#A238FF", "Deezer"),
            PlatformEntry(binding.capsuleYouTube, binding.tvYouTube, "#FF0033", "YouTube"),
            PlatformEntry(binding.capsuleSpotify, binding.tvSpotify, "#1DB954", "Spotify"),
            PlatformEntry(binding.capsuleJioSaavn, binding.tvJioSaavn, "#00D2C4", "JioSaavn")
        )

        fun updateDiamondAura(activeColor: Int) {
            val dm = resources.displayMetrics
            val radius = dm.widthPixels * 0.95f

            // True Diamond Radial Aura Gradient (Center Bright Glow -> Deep Dark Edge)
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

        fun select(target: PlatformEntry) {
            val activeColor = Color.parseColor(target.colorHex)
            val strokeOff = Color.parseColor("#25313D")
            val bgOff = Color.parseColor("#141B22")
            val textOff = Color.parseColor("#9CA3AF")

            entries.forEach { entry ->
                val isSelected = (entry == target)
                if (isSelected) {
                    entry.card.strokeColor = activeColor
                    entry.card.strokeWidth = 6
                    entry.card.setCardBackgroundColor(ColorStateList.valueOf(Color.parseColor("#1C2632")))
                    entry.tv.setTextColor(activeColor)
                    entry.tv.typeface = Typeface.DEFAULT_BOLD
                } else {
                    entry.card.strokeColor = strokeOff
                    entry.card.strokeWidth = 2
                    entry.card.setCardBackgroundColor(ColorStateList.valueOf(bgOff))
                    entry.tv.setTextColor(textOff)
                    entry.tv.typeface = Typeface.DEFAULT
                }
            }

            // Apply Diamond Radial Aura
            updateDiamondAura(activeColor)

            binding.searchBarContainer.strokeColor = activeColor
            binding.ivSearchIcon.imageTintList = ColorStateList.valueOf(activeColor)
            binding.etHomeSearch.hint = "Search songs in Savish ${target.name}..."

            feedData.refresh()
        }

        entries.forEach { entry ->
            entry.card.setOnClickListener { select(entry) }
        }

        // Default: All media diamond aura
        updateDiamondAura(Color.parseColor("#00E5FF"))
    }
}
