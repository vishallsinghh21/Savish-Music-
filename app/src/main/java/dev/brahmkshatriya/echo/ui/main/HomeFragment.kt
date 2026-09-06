package dev.brahmkshatriya.echo.ui.main

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
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

    private fun dp(ctx: Context, value: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            ctx.resources.displayMetrics
        ).toInt()
    }

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

        // Bypassing empty Unified Extension container
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

        renderDynamicCapsules(binding)
    }

    private fun renderDynamicCapsules(binding: FragmentHomeBinding) {
        val ctx = context ?: return
        val container = binding.layoutCapsulesContainer
        container.removeAllViews()

        data class CapsuleItem(val name: String, val colorHex: String)

        fun resolveColor(name: String): String {
            val s = name.lowercase()
            return when {
                "spotify" in s -> "#1DB954"
                "youtube" in s -> "#FF0033"
                "saavn" in s -> "#00D2C4"
                "deezer" in s -> "#A238FF"
                "apple" in s -> "#FC3C44"
                "offline" in s -> "#FF9900"
                "soundcloud" in s -> "#FF5500"
                else -> "#00E5FF"
            }
        }

        val items = mutableListOf<CapsuleItem>()
        items.add(CapsuleItem("All media", "#00E5FF"))
        items.add(CapsuleItem("Offline", "#FF9900"))
        items.add(CapsuleItem("YouTube", "#FF0033"))
        items.add(CapsuleItem("Spotify", "#1DB954"))
        items.add(CapsuleItem("JioSaavn", "#00D2C4"))
        items.add(CapsuleItem("Deezer", "#A238FF"))

        val cards = mutableListOf<Pair<MaterialCardView, TextView>>()

        fun applySelection(selectedCard: MaterialCardView, selectedTv: TextView, item: CapsuleItem) {
            val activeColor = Color.parseColor(item.colorHex)
            val strokeOff = Color.parseColor("#27333F")
            val bgOff = Color.parseColor("#141B22")
            val textOff = Color.parseColor("#9BA8B5")

            cards.forEach { (c, t) ->
                val active = (c == selectedCard)
                if (active) {
                    c.strokeColor = activeColor
                    c.strokeWidth = dp(ctx, 2.5f)
                    c.setCardBackgroundColor(ColorStateList.valueOf(Color.parseColor("#1C2632")))
                    t.setTextColor(activeColor)
                    t.typeface = Typeface.DEFAULT_BOLD
                } else {
                    c.strokeColor = strokeOff
                    c.strokeWidth = dp(ctx, 1f)
                    c.setCardBackgroundColor(ColorStateList.valueOf(bgOff))
                    t.setTextColor(textOff)
                    t.typeface = Typeface.DEFAULT
                }
            }

            binding.viewAmbientGlow.setBackgroundColor(activeColor)
            binding.searchBarContainer.strokeColor = activeColor
            binding.ivSearchIcon.imageTintList = ColorStateList.valueOf(activeColor)
            binding.etHomeSearch.hint = "Search songs in Savish ${item.name}..."

            feedData.refresh()
        }

        items.forEachIndexed { index, item ->
            val card = MaterialCardView(ctx).apply {
                radius = dp(ctx, 24f).toFloat()
                strokeWidth = dp(ctx, 1f)
                strokeColor = Color.parseColor("#27333F")
                setCardBackgroundColor(ColorStateList.valueOf(Color.parseColor("#141B22")))
                val lp = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    dp(ctx, 48f)
                )
                lp.setMargins(0, 0, dp(ctx, 10f), 0)
                layoutParams = lp
            }

            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(dp(ctx, 16f), 0, dp(ctx, 16f), 0)
            }

            val dot = View(ctx).apply {
                val dotLp = ViewGroup.MarginLayoutParams(
                    dp(ctx, 8f),
                    dp(ctx, 8f)
                )
                dotLp.setMargins(0, 0, dp(ctx, 8f), 0)
                layoutParams = dotLp
                background = ContextCompat.getDrawable(ctx, android.R.drawable.presence_online)
                backgroundTintList = ColorStateList.valueOf(Color.parseColor(item.colorHex))
            }

            val tv = TextView(ctx).apply {
                text = item.name
                textSize = 15f
                setTextColor(Color.parseColor("#9BA8B5"))
            }

            row.addView(dot)
            row.addView(tv)
            card.addView(row)
            cards.add(card to tv)

            card.setOnClickListener {
                applySelection(card, tv, item)
            }

            container.addView(card)

            if (index == 0) {
                applySelection(card, tv, item)
            }
        }
    }
}
