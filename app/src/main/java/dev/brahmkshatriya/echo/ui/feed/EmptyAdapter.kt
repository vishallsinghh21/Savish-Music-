package dev.brahmkshatriya.echo.ui.feed

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.core.view.isVisible
import androidx.paging.LoadState
import dev.brahmkshatriya.echo.R
import dev.brahmkshatriya.echo.databinding.ItemShelfEmptyBinding
import dev.brahmkshatriya.echo.ui.common.GridAdapter
import dev.brahmkshatriya.echo.utils.ui.scrolling.ScrollAnimLoadStateAdapter
import dev.brahmkshatriya.echo.utils.ui.scrolling.ScrollAnimViewHolder

class EmptyAdapter(
    private var config: Config? = null
) : ScrollAnimLoadStateAdapter<EmptyAdapter.ViewHolder>(), GridAdapter {

    data class ButtonConfig(
        val text: CharSequence,
        @DrawableRes val icon: Int? = null,
        val onClick: (View) -> Unit
    )

    data class Config(
        val title: CharSequence? = null,
        val subtitle: CharSequence? = null,
        val iconText: CharSequence? = null,
        @DrawableRes val iconDrawable: Int? = null,
        val primaryButton: ButtonConfig? = null,
        val secondaryButton: ButtonConfig? = null,
    )

    fun updateConfig(newConfig: Config?) {
        config = newConfig
        onEachViewHolder { bind(newConfig) }
    }

    class ViewHolder(val binding: ItemShelfEmptyBinding) : ScrollAnimViewHolder(binding.root) {
        fun bind(config: Config?) {
            val context = itemView.context

            // Icon handling
            if (config?.iconDrawable != null) {
                binding.emptyIconText.isVisible = false
                binding.emptyIconImage.isVisible = true
                binding.emptyIconImage.setImageResource(config.iconDrawable)
            } else {
                binding.emptyIconText.isVisible = true
                binding.emptyIconImage.isVisible = false
                binding.emptyIconText.text = config?.iconText ?: context.getString(R.string.crying_af)
            }

            // Title handling
            binding.emptyTitle.text = config?.title ?: context.getString(R.string.so_empty)

            // Subtitle handling
            if (config?.subtitle != null) {
                binding.emptySubtitle.isVisible = true
                binding.emptySubtitle.text = config.subtitle
            } else {
                binding.emptySubtitle.isVisible = false
            }

            // Buttons handling
            val hasPrimary = config?.primaryButton != null
            val hasSecondary = config?.secondaryButton != null

            binding.emptyActionsContainer.isVisible = hasPrimary || hasSecondary

            if (hasPrimary) {
                val primary = config!!.primaryButton!!
                binding.primaryActionButton.isVisible = true
                binding.primaryActionButton.text = primary.text
                if (primary.icon != null) {
                    binding.primaryActionButton.setIconResource(primary.icon)
                } else {
                    binding.primaryActionButton.icon = null
                }
                binding.primaryActionButton.setOnClickListener(primary.onClick)
            } else {
                binding.primaryActionButton.isVisible = false
                binding.primaryActionButton.setOnClickListener(null)
            }

            if (hasSecondary) {
                val secondary = config!!.secondaryButton!!
                binding.secondaryActionButton.isVisible = true
                binding.secondaryActionButton.text = secondary.text
                if (secondary.icon != null) {
                    binding.secondaryActionButton.setIconResource(secondary.icon)
                } else {
                    binding.secondaryActionButton.icon = null
                }
                binding.secondaryActionButton.setOnClickListener(secondary.onClick)
            } else {
                binding.secondaryActionButton.isVisible = false
                binding.secondaryActionButton.setOnClickListener(null)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, loadState: LoadState): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemShelfEmptyBinding.inflate(inflater, parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, loadState: LoadState) {
        super.onBindViewHolder(holder, loadState)
        holder.bind(config)
    }

    override val adapter = this
    override fun getSpanSize(position: Int, width: Int, count: Int) = count
}