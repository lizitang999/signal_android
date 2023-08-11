package org.mycrimes.insecuretests.badges.gifts.flow

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import org.signal.core.util.getParcelableArrayListCompat
import org.mycrimes.insecuretests.R
import org.mycrimes.insecuretests.contacts.paged.ContactSearchConfiguration
import org.mycrimes.insecuretests.contacts.paged.ContactSearchKey
import org.mycrimes.insecuretests.contacts.paged.ContactSearchState
import org.mycrimes.insecuretests.conversation.mutiselect.forward.MultiselectForwardFragment
import org.mycrimes.insecuretests.conversation.mutiselect.forward.MultiselectForwardFragmentArgs
import org.mycrimes.insecuretests.conversation.mutiselect.forward.SearchConfigurationProvider
import org.mycrimes.insecuretests.util.navigation.safeNavigate

/**
 * Allows the user to select a recipient to send a gift to.
 */
class GiftFlowRecipientSelectionFragment : Fragment(R.layout.gift_flow_recipient_selection_fragment), MultiselectForwardFragment.Callback, SearchConfigurationProvider {

  private val viewModel: GiftFlowViewModel by viewModels(
    ownerProducer = { requireActivity() }
  )

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
    toolbar.setNavigationOnClickListener { requireActivity().onBackPressed() }

    if (savedInstanceState == null) {
      childFragmentManager.beginTransaction()
        .replace(
          R.id.multiselect_container,
          MultiselectForwardFragment.create(
            MultiselectForwardFragmentArgs(
              canSendToNonPush = false,
              multiShareArgs = emptyList(),
              forceDisableAddMessage = true,
              selectSingleRecipient = true
            )
          )
        )
        .commit()
    }
  }

  override fun getSearchConfiguration(fragmentManager: FragmentManager, contactSearchState: ContactSearchState): ContactSearchConfiguration {
    return ContactSearchConfiguration.build {
      query = contactSearchState.query

      if (query.isNullOrEmpty()) {
        addSection(
          ContactSearchConfiguration.Section.Recents(
            includeSelf = false,
            includeHeader = true,
            mode = ContactSearchConfiguration.Section.Recents.Mode.INDIVIDUALS
          )
        )
      }

      addSection(
        ContactSearchConfiguration.Section.Individuals(
          includeSelf = false,
          transportType = ContactSearchConfiguration.TransportType.PUSH,
          includeHeader = true
        )
      )
    }
  }

  override fun onFinishForwardAction() = Unit

  override fun exitFlow() = Unit

  override fun onSearchInputFocused() = Unit

  override fun setResult(bundle: Bundle) {
    val contacts: List<ContactSearchKey.RecipientSearchKey> = bundle.getParcelableArrayListCompat(MultiselectForwardFragment.RESULT_SELECTION, ContactSearchKey.RecipientSearchKey::class.java)!!

    if (contacts.isNotEmpty()) {
      viewModel.setSelectedContact(contacts.first())
      findNavController().safeNavigate(R.id.action_giftFlowRecipientSelectionFragment_to_giftFlowConfirmationFragment)
    }
  }

  override fun getContainer(): ViewGroup = requireView() as ViewGroup

  override fun getDialogBackgroundColor(): Int = Color.TRANSPARENT
}
