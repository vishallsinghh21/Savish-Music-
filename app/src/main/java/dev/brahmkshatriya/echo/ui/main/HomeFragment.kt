package dev.brahmkshatriya.echo.ui.main

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import java.io.File
import java.io.FileOutputStream

class HomeFragment : Fragment(R.layout.fragment_home) {

    private val feedData by lazy {
        val vm by viewModel<FeedViewModel>()
        val id = "home"
        vm.getFeedData(id, EMPTY, cached = {
            val curr = current.value ?: return@getFeedData null
            val feed = Cached.getFeedShelf(app, curr.id, id).getOrNull()
            feed?.let { FeedData.State(curr.id, null, it) }
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

    private data class PlatformCapsule(val id: String?, val name: String, val colorHex: String)
    private var activePlatform = PlatformCapsule(null, "All media", "#00E5FF")

    private fun dp(ctx: Context, v: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            v,
            ctx.resources.displayMetrics
        ).toInt()
    }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { saveAndSetAvatar(it) }
    }

    private var bindingRef: FragmentHomeBinding? = null

    private fun saveAndSetAvatar(uri: Uri) {
        val ctx = context ?: return
        try {
            val inputStream = ctx.contentResolver.openInputStream(uri) ?: return
            val file = File(ctx.filesDir, "custom_profile_pic.png")
            val outputStream = FileOutputStream(file)
            inputStream.copyTo(outputStream)
            inputStream.close()
            outputStream.close()
            loadSavedAvatar()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadSavedAvatar() {
        val ctx = context ?: return
        val file = File(ctx.filesDir, "custom_profile_pic.png")
        if (file.exists()) {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            bindingRef?.ivProfileAvatar?.setImageBitmap(bitmap)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentHomeBinding.bind(view)
        bindingRef = binding
        setupTransition(view, false, MaterialSharedAxis.Y)

        // Exact applyInsets call signature matching Echo base
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

        // Bind directly to feedAdapter so duplicate middle button strips are excluded
        configureGridLayout(
            binding.recyclerView,
            feedAdapter
        )

        binding.swipeRefresh.run {
            setOnRefreshListener { feedData.refresh() }
            observe(feedData.isRefreshingFlow) {
                isRefreshing = it
            }
        }

        // Profile click: settings sheet
        binding.ivProfileAvatar.setOnClickListener {
            ExtensionsListBottomSheet.newInstance(ExtensionType.MUSIC)
                .show(parentFragmentManager, null)
        }

        // Profile long press: photo picker
        binding.ivProfileAvatar.setOnLongClickListener {
            pickImageLauncher.launch("image/*")
            true
        }

        loadSavedAvatar()

        binding.etHomeSearch.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(v.windowToken, 0)
                uiViewModel.navigation.value = 2
                true
            } else false
        }

        setupExtensionCapsules(binding)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        bindingRef = null
    }

    private fun setupExtensionCapsules(binding: FragmentHomeBinding) {
        val ctx = context ?: return
        val container = binding.layoutCapsulesContainer

        fun resolveColor(name: String): String {
            val s = name.lowercase()
            return when {
                "spotify" in s -> "#1DB954"
                "youtube" in s -> "#FF0033"
                "saavn" in s -> "#00D2C4"
                "deezer" in s -> "#A238FF"
                "offline" in s -> "#FFA500"
                "iheart" in s -> "#C92434"
                "kiss" in s -> "#E91E63"
                "anidb" in s -> "#FF8A00"
                "groove" in s -> "#0078D7"
                "radio" in s -> "#00B0FF"
                "khinsider" in s -> "#FF6D00"
                "opensubsonic" in s -> "#607D8B"
                "iptv" in s -> "#7C4DFF"
                "soundcloud" in s -> "#FF5500"
                "anikoto" in s -> "#00BCD4"
                "drive" in s -> "#FFC107"
                else -> "#00E5FF"
            }
        }

        fun updateDiamondAura(activeColor: Int) {
            val dm = resources.displayMetrics
            val radius = dm.widthPixels * 0.95f
            val radialGradient = GradientDrawable().apply {
                gradientType = GradientDrawable.RADIAL_GRADIENT
                gradientRadius = radius
                setGradientCenter(0.5f, 0.28f)
                colors = intArrayOf(
                    ColorUtils.setAlphaComponent(activeColor, 120),
                    ColorUtils.setAlphaComponent(activeColor, 40),
                    Color.parseColor("#070A0F")
                )
            }
            binding.viewAmbientGlow.background = radialGradient
        }

        val platformSources = listOf(
            "All media", "Offline", "YouTube Music", "Spotify", "JioSaavn",
            "SoundCloud", "Deezer", "iHeartRadio", "KissKH", "AniDB",
            "Groove Music", "Radio Browser", "KHInsider", "OpenSubsonic",
            "IPTV", "Anikoto", "Google Drive"
        )

        val dynamicList = platformSources.map { name ->
            val isAll = name == "All media"
            PlatformCapsule(if (isAll) null else name.lowercase(), name, resolveColor(name))
        }

        container.removeAllViews()
        val cards = mutableListOf<Pair<MaterialCardView, PlatformCapsule>>()

        fun applySelection(targetCard: MaterialCardView, item: PlatformCapsule) {
            activePlatform = item
            val activeColor = Color.parseColor(item.colorHex)
            val strokeOff = Color.parseColor("#25313D")
            val bgOff = Color.parseColor("#141B22")

            cards.forEach { (c, _) ->
                val isSel = (c == targetCard)
                if (isSel) {
                    c.strokeColor = activeColor
                    c.strokeWidth = dp(ctx, 2.5f)
                    c.setCardBackgroundColor(
                        ColorStateList.valueOf(ColorUtils.setAlphaComponent(activeColor, 45))
                    )
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

            feedData.refresh()
        }

        dynamicList.forEachIndexed { index, item ->
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
                setPadding(dp(ctx, 18f), 0, dp(dp(ctx, 1f).toFloat(), 0f).coerceAtLeast(0), 0)
            }

            val dot = View(ctx).apply {
                val dotLp = ViewGroup.MarginLayoutParams(dp(ctx, 9f), dp(ctx, 9f))
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
                applySelection(card, item)
            }

            container.addView(card)

            if (index == 0) {
                applySelection(card, item)
            }
        }
    }
}
