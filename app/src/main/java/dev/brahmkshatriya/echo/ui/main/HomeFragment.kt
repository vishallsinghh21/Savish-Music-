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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.transition.MaterialSharedAxis
import dev.brahmkshatriya.echo.R
import dev.brahmkshatriya.echo.common.clients.HomeFeedClient
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
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
            val curr = current.value ?: return@getFeedData null
            val feed = Cached.getFeedShelf(app, curr.id, id).getOrNull()
            FeedData.State(curr.id, null, feed)
        }) {
            val curr = current.value ?: return@getFeedData null
            val feed = Cached.savingFeed(
                app, curr, id,
                curr.getAs<HomeFeedClient, Feed<Shelf>> { loadHomeFeed() }.getOrThrow()
            )
            FeedData.State(curr.id, null, feed)
        }
    }

    private val listener by lazy { getFeedListener(requireParentFragment()) }
    private val feedAdapter by lazy { getFeedAdapter(feedData, listener) }

    private data class PlatformChip(val id: String?, val name: String, val colorHex: String)
    private var selectedPlatform = PlatformChip(null, "All media", "#00E5FF")

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
            feedAdapter.withLoading(this)
        )

        binding.ivProfile.setOnClickListener {
            ExtensionsListBottomSheet.newInstance(ExtensionType.MUSIC)
                .show(parentFragmentManager, null)
        }

        binding.etHomeSearch.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = binding.etHomeSearch.text?.toString()?.trim()
                if (!query.isNullOrEmpty()) {
                    val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                    imm?.hideSoftInputFromWindow(v.windowToken, 0)
                    uiViewModel.searchQuery.value = query
                    uiViewModel.navigation.value = 2
                }
                true
            } else false
        }

        setupPlatformSystem(binding)
    }

    private fun setupPlatformSystem(binding: FragmentHomeBinding) {
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

        val platformList = mutableListOf(
            PlatformChip(null, "All media", "#00E5FF"),
            PlatformChip("offline", "Offline", "#FF9900"),
            PlatformChip("youtube", "YouTube", "#FF0033"),
            PlatformChip("spotify", "Spotify", "#1DB954"),
            PlatformChip("jiosaavn", "JioSaavn", "#00D2C4"),
            PlatformChip("deezer", "Deezer", "#A238FF")
        )

        // Read active extension if available
        feedData.current.value?.let { curr ->
            if (platformList.none { it.name.equals(curr.name, ignoreCase = true) }) {
                platformList.add(PlatformChip(curr.id, curr.name, resolveColor(curr.name)))
            }
        }

        binding.rvPlatformCapsules.layoutManager =
            LinearLayoutManager(ctx, LinearLayoutManager.HORIZONTAL, false)

        class CapsuleAdapter : RecyclerView.Adapter<CapsuleAdapter.ViewHolder>() {
            inner class ViewHolder(
                val card: MaterialCardView,
                val dot: View,
                val text: TextView
            ) : RecyclerView.ViewHolder(card)

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
                val card = MaterialCardView(ctx).apply {
                    radius = dp(ctx, 20f).toFloat()
                    strokeWidth = dp(ctx, 1.2f)
                    strokeColor = Color.parseColor("#25313D")
                    setCardBackgroundColor(ColorStateList.valueOf(Color.parseColor("#141B22")))
                    layoutParams = ViewGroup.MarginLayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        dp(ctx, 40f)
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

                val text = TextView(ctx).apply {
                    textSize = 14f
                    setTextColor(Color.WHITE)
                    typeface = Typeface.DEFAULT_BOLD
                }

                row.addView(dot)
                row.addView(text)
                card.addView(row)
                return ViewHolder(card, dot, text)
            }

            override fun onBindViewHolder(holder: ViewHolder, position: Int) {
                val item = platformList[position]
                holder.text.text = item.name
                val color = Color.parseColor(item.colorHex)
                holder.dot.backgroundTintList = ColorStateList.valueOf(color)

                val isSelected = (selectedPlatform.name == item.name)
                if (isSelected) {
                    holder.card.strokeColor = color
                    holder.card.strokeWidth = dp(ctx, 2.5f)
                    holder.card.setCardBackgroundColor(
                        ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, 45))
                    )
                } else {
                    holder.card.strokeColor = Color.parseColor("#25313D")
                    holder.card.strokeWidth = dp(ctx, 1.2f)
                    holder.card.setCardBackgroundColor(
                        ColorStateList.valueOf(Color.parseColor("#141B22"))
                    )
                }

                holder.card.setOnClickListener {
                    selectedPlatform = item
                    notifyDataSetChanged()

                    val activeColor = Color.parseColor(item.colorHex)
                    updateAura(activeColor)
                    binding.searchBarContainer.backgroundTintList =
                        ColorStateList.valueOf(ColorUtils.setAlphaComponent(activeColor, 35))
                    binding.etHomeSearch.hint = "Search songs in Savish ${item.name}..."

                    feedData.refresh()
                }
            }

            override fun getItemCount() = platformList.size
        }

        binding.rvPlatformCapsules.adapter = CapsuleAdapter()
        updateAura(Color.parseColor(selectedPlatform.colorHex))
    }
}
