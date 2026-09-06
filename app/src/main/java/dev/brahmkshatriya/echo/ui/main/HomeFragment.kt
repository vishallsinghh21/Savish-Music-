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
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.transition.MaterialSharedAxis
import dev.brahmkshatriya.echo.R
import dev.brahmkshatriya.echo.common.clients.HomeFeedClient
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.Extension
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
import dev.brahmkshatriya.echo.ui.feed.FeedAdapter
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
            val curr = current.value ?: return@getFeedData null
            val feed = Cached.getFeedShelf(app, curr.id, id).getOrNull()
            FeedData.State(curr.id, null, filterContentOnly(feed))
        }) {
            val curr = current.value ?: return@getFeedData null
            val rawFeed = Cached.savingFeed(
                app, curr, id,
                curr.getAs<HomeFeedClient, Feed<Shelf>> { loadHomeFeed() }.getOrThrow()
            )
            FeedData.State(curr.id, null, filterContentOnly(rawFeed))
        }
    }

    private val listener by lazy { getFeedListener(requireParentFragment()) }
    private val rawFeedAdapter by lazy { getFeedAdapter(feedData, listener) }

    // Intercept and strip out category chips, duplicate buttons & shelf headers
    private val feedAdapter by lazy {
        object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun getItemCount() = rawFeedAdapter.itemCount
            override fun getItemViewType(position: Int) = rawFeedAdapter.getItemViewType(position)

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val holder = rawFeedAdapter.onCreateViewHolder(parent, viewType)
                // If item matches header/category/button shelf, collapse its bounds entirely
                if (viewType == R.layout.item_shelf_category ||
                    viewType == R.layout.item_shelf_header ||
                    viewType == R.layout.item_shelf_empty
                ) {
                    holder.itemView.layoutParams = RecyclerView.LayoutParams(0, 0).apply {
                        visibility = View.GONE
                    }
                }
                return holder
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                if (holder.itemView.layoutParams.height == 0) {
                    holder.itemView.visibility = View.GONE
                    return
                }
                rawFeedAdapter.onBindViewHolder(holder, position)
            }
        }
    }

    private data class DynamicPlatform(
        val id: String?,
        val name: String,
        val colorHex: String,
        val extension: Extension? = null
    )

    private var activePlatform = DynamicPlatform(null, "All media", "#00E5FF")

    private fun dp(ctx: Context, v: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            v,
            ctx.resources.displayMetrics
        ).toInt()
    }

    private fun filterContentOnly(feed: Feed<Shelf>?): Feed<Shelf>? {
        if (feed == null) return null
        val cleanShelves = feed.items.filter { shelf ->
            val title = shelf.title.lowercase()
            !title.contains("category") &&
            !title.contains("filter") &&
            !title.contains("source") &&
            shelf.items.isNotEmpty()
        }
        return feed.copy(items = cleanShelves)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentHomeBinding.bind(view)
        setupTransition(view, false, MaterialSharedAxis.Y)
        applyInsets(binding.recyclerView, binding.appBarLayout)

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

        configureGridLayout(
            binding.recyclerView,
            feedAdapter
        )

        // Profile avatar opens extension/settings manager
        binding.ivProfile.setOnClickListener {
            ExtensionsListBottomSheet.newInstance(ExtensionType.MUSIC)
                .show(parentFragmentManager, null)
        }

        // Platform-aware search trigger
        binding.etHomeSearch.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = binding.etHomeSearch.text?.toString()?.trim()
                if (!query.isNullOrEmpty()) {
                    val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                    imm?.hideSoftInputFromWindow(v.windowToken, 0)

                    // Forward query with platform filter context to search page
                    uiViewModel.searchQuery.value = query
                    uiViewModel.navigation.value = 2
                }
                true
            } else false
        }

        setupLivePlatformCapsules(binding, uiViewModel)
    }

    private fun setupLivePlatformCapsules(binding: FragmentHomeBinding, uiViewModel: UiViewModel) {
        val ctx = context ?: return

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
                "iheart" in s -> "#C92434"
                "kiss" in s -> "#E91E63"
                "groove" in s -> "#0078D7"
                else -> "#00E5FF"
            }
        }

        fun updateAura(activeColor: Int) {
            val dm = resources.displayMetrics
            val radius = dm.widthPixels * 0.95f
            val radialGradient = GradientDrawable().apply {
                gradientType = GradientDrawable.RADIAL_GRADIENT
                gradientRadius = radius
                setGradientCenter(0.5f, 0.25f)
                colors = intArrayOf(
                    ColorUtils.setAlphaComponent(activeColor, 110),
                    ColorUtils.setAlphaComponent(activeColor, 35),
                    Color.parseColor("#070A0F")
                )
            }
            binding.homeRoot.background = radialGradient
        }

        // Observe actual installed extensions dynamically
        observe(uiViewModel.extensions) { installedExtensions ->
            val list = mutableListOf(
                DynamicPlatform(null, "All media", "#00E5FF")
            )

            installedExtensions.forEach { ext ->
                list.add(DynamicPlatform(ext.id, ext.name, resolveColor(ext.name), ext))
            }

            // Bind capsules inside RecyclerView container
            binding.rvPlatformCapsules.adapter = object : RecyclerView.Adapter<CapsuleViewHolder>() {
                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CapsuleViewHolder {
                    val card = MaterialCardView(ctx).apply {
                        radius = dp(ctx, 22f).toFloat()
                        strokeWidth = dp(ctx, 1.2f)
                        strokeColor = Color.parseColor("#25313D")
                        setCardBackgroundColor(ColorStateList.valueOf(Color.parseColor("#141B22")))
                        layoutParams = ViewGroup.MarginLayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            dp(ctx, 42f)
                        ).apply {
                            setMargins(0, 0, dp(ctx, 8f), 0)
                        }
                    }

                    val row = LinearLayout(ctx).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER
                        setPadding(dp(ctx, 16f), 0, dp(ctx, 16f), 0)
                    }

                    val dot = View(ctx).apply {
                        layoutParams = ViewGroup.MarginLayoutParams(dp(ctx, 8f), dp(ctx, 8f)).apply {
                            setMargins(0, 0, dp(ctx, 8f), 0)
                        }
                        background = ContextCompat.getDrawable(ctx, android.R.drawable.presence_online)
                    }

                    val tv = TextView(ctx).apply {
                        textSize = 14f
                        setTextColor(Color.WHITE)
                        typeface = Typeface.DEFAULT_BOLD
                    }

                    row.addView(dot)
                    row.addView(tv)
                    card.addView(row)
                    return CapsuleViewHolder(card, dot, tv)
                }

                override fun onBindViewHolder(holder: CapsuleViewHolder, position: Int) {
                    val item = list[position]
                    holder.title.text = item.name
                    val platformColor = Color.parseColor(item.colorHex)
                    holder.dot.backgroundTintList = ColorStateList.valueOf(platformColor)

                    val isSelected = (activePlatform.id == item.id)
                    if (isSelected) {
                        holder.card.strokeColor = platformColor
                        holder.card.strokeWidth = dp(ctx, 2.5f)
                        holder.card.setCardBackgroundColor(
                            ColorStateList.valueOf(ColorUtils.setAlphaComponent(platformColor, 40))
                        )
                    } else {
                        holder.card.strokeColor = Color.parseColor("#25313D")
                        holder.card.strokeWidth = dp(ctx, 1.2f)
                        holder.card.setCardBackgroundColor(
                            ColorStateList.valueOf(Color.parseColor("#141B22"))
                        )
                    }

                    holder.card.setOnClickListener {
                        activePlatform = item
                        notifyDataSetChanged()

                        // 1. Dynamic Search placeholder & Aura color
                        binding.etHomeSearch.hint = "Search songs in Savish ${item.name}..."
                        updateAura(platformColor)

                        // 2. Switch feed context
                        if (item.extension != null) {
                            feedData.current.value = item.extension
                        } else {
                            // All Media mode: refresh all
                            feedData.refresh()
                        }
                    }
                }

                override fun getItemCount() = list.size
            }

            // Set initial state
            updateAura(Color.parseColor(activePlatform.colorHex))
        }
    }

    private class CapsuleViewHolder(
        val card: MaterialCardView,
        val dot: View,
        val title: TextView
    ) : RecyclerView.ViewHolder(card)
}
