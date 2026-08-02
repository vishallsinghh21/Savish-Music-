package dev.brahmkshatriya.echo.ui.media

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.paging.LoadState
import dev.brahmkshatriya.echo.R
import dev.brahmkshatriya.echo.common.models.Feed
import dev.brahmkshatriya.echo.common.models.Playlist
import dev.brahmkshatriya.echo.databinding.FragmentMediaDetailsBinding
import dev.brahmkshatriya.echo.ui.common.FragmentUtils.openFragment
import dev.brahmkshatriya.echo.ui.common.GridAdapter
import dev.brahmkshatriya.echo.ui.common.GridAdapter.Companion.configureGridLayout
import dev.brahmkshatriya.echo.ui.common.UiViewModel
import dev.brahmkshatriya.echo.ui.common.UiViewModel.Companion.applyContentInsets
import dev.brahmkshatriya.echo.ui.common.UiViewModel.Companion.applyInsets
import dev.brahmkshatriya.echo.ui.common.UiViewModel.Companion.configure
import dev.brahmkshatriya.echo.ui.feed.EmptyAdapter
import dev.brahmkshatriya.echo.ui.feed.FeedAdapter.Companion.getFeedAdapter
import dev.brahmkshatriya.echo.ui.feed.FeedAdapter.Companion.getTouchHelper
import dev.brahmkshatriya.echo.ui.feed.FeedClickListener
import dev.brahmkshatriya.echo.ui.feed.FeedClickListener.Companion.getFeedListener
import dev.brahmkshatriya.echo.ui.feed.FeedViewModel
import dev.brahmkshatriya.echo.ui.media.MediaHeaderAdapter.Companion.getMediaHeaderListener
import dev.brahmkshatriya.echo.ui.playlist.edit.EditPlaylistFragment
import dev.brahmkshatriya.echo.utils.ContextUtils.observe
import dev.brahmkshatriya.echo.utils.ui.FastScrollerHelper
import kotlinx.coroutines.flow.combine
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

class MediaDetailsFragment : Fragment(R.layout.fragment_media_details) {

    interface Parent {
        val feedId: String
        val viewModel: MediaDetailsViewModel
        val fromPlayer: Boolean
    }

    val parent by lazy { requireParentFragment() as Parent }
    val viewModel by lazy { parent.viewModel }
    private val feedViewModel by lazy {
        requireParentFragment().viewModel<FeedViewModel>().value
    }

    private val trackFeedData by lazy {
        feedViewModel.getFeedData(
            "${parent.feedId}_tracks",
            Feed.Buttons(showPlayAndShuffle = true),
            true,
            viewModel.tracksLoadedFlow, viewModel.trackCachedFlow,
            cached = { viewModel.trackCachedFlow.value?.getOrThrow() },
            loader = { viewModel.tracksLoadedFlow.value?.getOrThrow() }
        )
    }

    private val feedData by lazy {
        feedViewModel.getFeedData(
            "${parent.feedId}_feed",
            Feed.Buttons(),
            false,
            viewModel.feedCachedFlow, viewModel.feedLoadedFlow,
            cached = { viewModel.feedCachedFlow.value?.getOrThrow() },
            loader = { viewModel.feedLoadedFlow.value?.getOrThrow() }
        )
    }

    private val mediaHeaderAdapter by lazy {
        MediaHeaderAdapter(
            requireParentFragment().getMediaHeaderListener(viewModel),
            parent.fromPlayer
        )
    }

    private val feedListener by lazy {
        if (!parent.fromPlayer) requireParentFragment().getFeedListener()
        else FeedClickListener(
            requireParentFragment(),
            requireActivity().supportFragmentManager,
            R.id.navHostFragment
        ) {
            val uiViewModel by activityViewModel<UiViewModel>()
            uiViewModel.collapsePlayer()
        }
    }

    private val trackEmptyAdapter by lazy { EmptyAdapter() }
    private val trackAdapter by lazy {
        getFeedAdapter(trackFeedData, feedListener, true)
    }
    private val feedAdapter by lazy {
        getFeedAdapter(feedData, feedListener)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentMediaDetailsBinding.bind(view)
        val uiViewModel by activityViewModel<UiViewModel>()
        FastScrollerHelper.applyTo(binding.recyclerView)
        applyInsets(viewModel.uiResultFlow) {
            val item = viewModel.uiResultFlow.value?.getOrNull()?.item as? Playlist
            val bottom = if (item?.isEditable == true) 72 else 16
            binding.recyclerView.applyContentInsets(it, 20, 8, bottom)
        }
        val lineAdapter = LineAdapter()
        observe(trackFeedData.shouldShowEmpty) {
            lineAdapter.loadState = if (it) LoadState.Loading else LoadState.NotLoading(false)
        }
        observe(viewModel.uiResultFlow) { result ->
            mediaHeaderAdapter.result = result
            val item = result?.getOrNull()?.item
            val (extensionId, _, loaded) = viewModel.getItem() ?: Triple("", null, false)
            if (item is Playlist) {
                if (item.isEditable) {
                    trackEmptyAdapter.updateConfig(
                        EmptyAdapter.Config(
                            title = getString(R.string.empty_playlist_title),
                            subtitle = getString(R.string.empty_playlist_editable_desc),
                            primaryButton = EmptyAdapter.ButtonConfig(
                                text = getString(R.string.add_songs),
                                icon = R.drawable.ic_playlist_add
                            ) {
                                requireActivity().openFragment<EditPlaylistFragment>(
                                    null,
                                    EditPlaylistFragment.getBundle(extensionId, item, loaded)
                                )
                            },
                            secondaryButton = EmptyAdapter.ButtonConfig(
                                text = getString(R.string.explore_music),
                                icon = R.drawable.ic_search_filled
                            ) {
                                uiViewModel.navigation.value = 1
                            }
                        )
                    )
                } else {
                    trackEmptyAdapter.updateConfig(
                        EmptyAdapter.Config(
                            title = getString(R.string.empty_playlist_title),
                            subtitle = getString(R.string.empty_playlist_desc),
                            primaryButton = EmptyAdapter.ButtonConfig(
                                text = getString(R.string.explore_music),
                                icon = R.drawable.ic_search_filled
                            ) {
                                uiViewModel.navigation.value = 1
                            }
                        )
                    )
                }
            } else {
                trackEmptyAdapter.updateConfig(
                    EmptyAdapter.Config(
                        title = getString(R.string.so_empty),
                        primaryButton = EmptyAdapter.ButtonConfig(
                            text = getString(R.string.explore_music),
                            icon = R.drawable.ic_search_filled
                        ) {
                            uiViewModel.navigation.value = 1
                        }
                    )
                )
            }
        }
        getTouchHelper(feedListener).attachToRecyclerView(binding.recyclerView)
        configureGridLayout(
            binding.recyclerView,
            GridAdapter.Concat(
                mediaHeaderAdapter,
                trackAdapter.withLoading(this, trackEmptyAdapter),
                lineAdapter,
                feedAdapter.withLoading(this)
            )
        )
        val loadingFlow = viewModel.isRefreshingFlow
            .combine(trackFeedData.isRefreshingFlow) { a, b -> a || b }
                .combine(feedData.isRefreshingFlow) { a, b -> a || b }
        binding.swipeRefresh.run {
            configure()
            setOnRefreshListener { viewModel.refresh() }
            observe(loadingFlow) {
                isRefreshing = it
            }
        }
    }
}