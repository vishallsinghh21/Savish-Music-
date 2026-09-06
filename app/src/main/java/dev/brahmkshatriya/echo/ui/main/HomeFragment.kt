package dev.brahmkshatriya.echo.ui.main

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
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

        // Unified Extension Header ko hata kar direct clean feed load karna
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

        setupCapsuleTriggers(binding)
    }

    private fun setupCapsuleTriggers(binding: FragmentHomeBinding) {
        val capsules = listOf(
            Triple(binding.capsuleAllMedia, "#1500E5FF", "All media"),
            Triple(binding.capsuleYouTube, "#18FF0033", "YouTube"),
            Triple(binding.capsuleSpotify, "#181DB954", "Spotify"),
            Triple(binding.capsuleJioSaavn, "#1800B0FF", "JioSaavn")
        )

        fun selectCapsule(selectedCard: MaterialCardView, glowColor: String, platformName: String) {
            capsules.forEach { (card, _, _) ->
                if (card == selectedCard) {
                    card.setCardBackgroundColor(Color.parseColor("#1F2933"))
                    card.strokeWidth = 2
                } else {
                    card.setCardBackgroundColor(Color.parseCllolor("#141B22"))
                    card.strokeWidth = 1
                }
            }
            binding.viewAmbientGlow.setBackgroundColor(Color.parseColor(glowColor))
            binding.etHomeSearch.hint = "Search songs in Savish $platformName..."
            
            // Switch current active extension/feed
            feedData.current.value?.let { curr ->
                feedData.refresh()
            }
        }

        binding.capsuleAllMedia.setOnClickListener {
            selectCapsule(binding.capsuleAllMedia, "#1500E5FF", "All media")
        }
        binding.capsuleYouTube.setOnClickListener {
            selectCapsule(binding.capsuleYouTube, "#18FF0033", "YouTube")
        }
        binding.capsuleSpotify.setOnClickListener {
            selectCapsule(binding.capsuleSpotify, "#181DB954", "Spotify")
        }
        binding.capsuleJioSaavn.setOnClickListener {
            selectCapsule(binding.capsuleJioSaavn, "#1800B0FF", "JioSaavn")
        }
    }
}
