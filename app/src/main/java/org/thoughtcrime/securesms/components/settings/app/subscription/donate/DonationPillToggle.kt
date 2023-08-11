package org.mycrimes.insecuretests.components.settings.app.subscription.donate

import com.google.android.material.button.MaterialButton
import org.mycrimes.insecuretests.R
import org.mycrimes.insecuretests.databinding.DonationPillToggleBinding
import org.mycrimes.insecuretests.util.adapter.mapping.BindingFactory
import org.mycrimes.insecuretests.util.adapter.mapping.BindingViewHolder
import org.mycrimes.insecuretests.util.adapter.mapping.MappingAdapter
import org.mycrimes.insecuretests.util.adapter.mapping.MappingModel

object DonationPillToggle {

  fun register(mappingAdapter: MappingAdapter) {
    mappingAdapter.registerFactory(Model::class.java, BindingFactory(::ViewHolder, DonationPillToggleBinding::inflate))
  }

  class Model(
    val isEnabled: Boolean,
    val selected: DonateToSignalType,
    val onClick: () -> Unit
  ) : MappingModel<Model> {
    override fun areItemsTheSame(newItem: Model): Boolean = true

    override fun areContentsTheSame(newItem: Model): Boolean {
      return isEnabled == newItem.isEnabled && selected == newItem.selected
    }
  }

  private class ViewHolder(binding: DonationPillToggleBinding) : BindingViewHolder<Model, DonationPillToggleBinding>(binding) {
    override fun bind(model: Model) {
      when (model.selected) {
        DonateToSignalType.ONE_TIME -> {
          presentButtons(model, binding.oneTime, binding.monthly)
        }
        DonateToSignalType.MONTHLY -> {
          presentButtons(model, binding.monthly, binding.oneTime)
        }
        DonateToSignalType.GIFT -> {
          error("Unsupported donation type.")
        }
      }
    }

    private fun presentButtons(model: Model, selected: MaterialButton, notSelected: MaterialButton) {
      selected.setOnClickListener(null)
      notSelected.setOnClickListener { model.onClick() }
      selected.isSelected = true
      notSelected.isSelected = false
      selected.setIconResource(R.drawable.ic_check_24)
      notSelected.icon = null
    }
  }
}
