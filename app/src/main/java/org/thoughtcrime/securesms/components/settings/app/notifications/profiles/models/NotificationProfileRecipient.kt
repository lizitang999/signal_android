package org.mycrimes.insecuretests.components.settings.app.notifications.profiles.models

import android.view.View
import org.mycrimes.insecuretests.R
import org.mycrimes.insecuretests.components.settings.PreferenceModel
import org.mycrimes.insecuretests.components.settings.conversation.preferences.RecipientPreference
import org.mycrimes.insecuretests.recipients.RecipientId
import org.mycrimes.insecuretests.util.adapter.mapping.LayoutFactory
import org.mycrimes.insecuretests.util.adapter.mapping.MappingAdapter
import org.mycrimes.insecuretests.util.adapter.mapping.MappingViewHolder

/**
 * DSL custom preference for showing recipients in a profile. Delegates most work to [RecipientPreference].
 */
object NotificationProfileRecipient {

  fun register(adapter: MappingAdapter) {
    adapter.registerFactory(Model::class.java, LayoutFactory({ ViewHolder(it) }, R.layout.notification_profile_recipient_list_item))
  }

  class Model(val recipientModel: RecipientPreference.Model, val onRemoveClick: (RecipientId) -> Unit) : PreferenceModel<Model>() {
    override fun areItemsTheSame(newItem: Model): Boolean {
      return recipientModel.recipient.id == newItem.recipientModel.recipient.id
    }

    override fun areContentsTheSame(newItem: Model): Boolean {
      return super.areContentsTheSame(newItem) && recipientModel.areContentsTheSame(newItem.recipientModel)
    }
  }

  class ViewHolder(itemView: View) : MappingViewHolder<Model>(itemView) {

    private val recipientViewHolder: RecipientPreference.ViewHolder = RecipientPreference.ViewHolder(itemView)
    private val remove: View = findViewById(R.id.recipient_remove)

    override fun bind(model: Model) {
      recipientViewHolder.bind(model.recipientModel)
      remove.setOnClickListener { model.onRemoveClick(model.recipientModel.recipient.id) }
    }
  }
}
