package dev.brahmkshatriya.echo.ui.main

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import com.google.android.material.transition.MaterialSharedAxis
import dev.brahmkshatriya.echo.R
import dev.brahmkshatriya.echo.common.clients.HomeFeedClient
import dev.brahmkshatriya.echo.common.models.EchoExtension
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
import dev.brahmkshatriya.echo.ui.settings.SettingsActivity
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

    private fun dp(ctx: Context, v: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            v,
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

        // Profile click opens App Settings
        binding.ivProfileAvatar.setOnClickListener {
            startActivity(SettingsActivity.getIntent(requireContext()))
        }

        // Search trigger on keyboard enter
        binding.etHomeSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = binding.etHomeSearch.text?.toString()?.trim()
                if (!query.isNullOrEmpty()) {
                    uiViewModel.navigation.value = 2 // Switch to search
                }
                true
            } else false
        }

        setupDynamicCapsules(binding)
    }

    private fun setupDynamicCapsules(binding: FragmentHomeBinding) {
        val ctx = context ?: return
        val container = binding.layoutCapsulesContainer

        data class CapsuleItem(val id: String?, val name: String, val colorHex: String)

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
                "drive" in s -> "#FFC107"
                "iheart" in s -> "#C92434"
                else -> "#00E5FF"
            }
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

        fun renderCapsules(list: List<CapsuleItem>) {
            container.removeAllViews()
            val cards = mutableListOf<Pair<MaterialCardView, CapsuleItem>>()

            fun selectItem(targetCard: MaterialCardView, item: CapsuleItem) {
                val activeColor = Color.parseColor(item.colorHex)
                val strokeOff = Color.parseColor("#25313D")
                val bgOff = Color.parseColor("#141B22")

                cards.forEach { (c, _) ->
                    val isSel = (c == targetCard)
                    if (isSel) {
                        c.strokeColor = activeColor
                        c.strokeWidth = dp(ctx, 2.5f)
                        c.setCardBackgroundColor(ColorStateList.valueOf(Color.parseColor("#1C2632")))
                    } else {
                        c.strokeColor = strokeOff
                        c.strokeWidth = dp(ctx, 1.2f)
                        c.setCardBackgroundColor(ColorStateList.valueOf(bgOff))
                    }
                }

                updateDiamondAura(activeColor)
                binding.searchBarContainer.strokeColor = activeColor
                binding.ivSearchIcon.imageTintList = ColorStateList.valueOf(activeColor)
                binding.etHomeSearch.hint = "Search songs in Savish ${item.name}..."

                if (item.id != null) {
                    extensionLoader.loadedExtensions.value.find { it.id == item.id }?.let { ext ->
                        feedData.current.value = ext
                    }
                }
                feedData.refresh()
            }

            list.forEachIndexed { index, item ->
                val card = MaterialCardView(ctx).apply {
                    radius = dp(ctx, 24f).toFloat()
                    strokeWidth = dp(ctx, 1.2f)
                    strokeColor = Color.parseColor("#25313D")
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
                    setPadding(dp(ctx, 18f), 0, dp(ctx, 18f), 0)
                }

                val dot = View(ctx).apply {
                    val dotLp = ViewGroup.MarginLayoutParams(dp(ctx, 10f), dp(ctx, 10f))
                    dotLp.setMargins(0, 0, dp(ctx, 8f), 0)
                    layoutParams = dotLp
                    background = ContextCompat.getDrawable(ctx, android.R.drawable.presence_online)
                    backgroundTintList = ColorStateList.valueOf(Color.parseColor(item.colorHex))
                }

                val tv = TextView(ctx).apply {
                    text = item.name
                    textSize = 15f
                    setTextColor(Color.WHITE)
                    typeface = Typeface.DEFAULT_BOLD
                }

                row.addView(dot)
                row.addView(tv)
                card.addView(row)
                cards.add(card to item)

                card.setOnClickListener {
                    selectItem(card, item)
                }

                container.addView(card)

                if (index == 0) {
                    selectItem(card, item)
                }
            }
        }

        // Loaded extensions se automatically capsule list populate hogi
        observe(extensionLoader.loadedExtensions) { exts: List<EchoExtension> ->
            val list = mutableListOf<CapsuleItem>()
            list.add(CapsuleItem(null, "All media", "#00E5FF"))
            list.add(CapsuleItem("offline", "Offline", "#FF9900"))
            exts.forEach { ext ->
                list.add(CapsuleItem(ext.id, ext.name, resolveColor(ext.name)))
            }
            renderCapsules(list)
        }
    }
}
