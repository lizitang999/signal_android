package org.mycrimes.insecuretests.conversation.v2

import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import org.signal.core.util.Result
import org.mycrimes.insecuretests.groups.ui.GroupChangeFailureReason
import org.mycrimes.insecuretests.messagerequests.MessageRequestRepository
import org.mycrimes.insecuretests.recipients.RecipientId

/**
 * View model for interacting with a message request displayed in ConversationFragment V2
 */
class MessageRequestViewModel(
  private val threadId: Long,
  private val recipientRepository: ConversationRecipientRepository,
  private val messageRequestRepository: MessageRequestRepository
) : ViewModel() {

  private val recipientId: Single<RecipientId>
    get() {
      return recipientRepository
        .conversationRecipient
        .map { it.id }
        .firstOrError()
    }

  fun onAccept(): Single<Result<Unit, GroupChangeFailureReason>> {
    return recipientId
      .flatMap { recipientId ->
        messageRequestRepository.acceptMessageRequest(recipientId, threadId)
      }
      .observeOn(AndroidSchedulers.mainThread())
  }

  fun onDelete(): Single<Result<Unit, GroupChangeFailureReason>> {
    return recipientId
      .flatMap { recipientId ->
        messageRequestRepository.deleteMessageRequest(recipientId, threadId)
      }
      .observeOn(AndroidSchedulers.mainThread())
  }

  fun onBlock(): Single<Result<Unit, GroupChangeFailureReason>> {
    return recipientId
      .flatMap { recipientId ->
        messageRequestRepository.blockMessageRequest(recipientId)
      }
      .observeOn(AndroidSchedulers.mainThread())
  }

  fun onUnblock(): Single<Result<Unit, GroupChangeFailureReason>> {
    return recipientId
      .flatMap { recipientId ->
        messageRequestRepository.unblockAndAccept(recipientId)
      }
      .observeOn(AndroidSchedulers.mainThread())
  }

  fun onBlockAndReportSpam(): Single<Result<Unit, GroupChangeFailureReason>> {
    return recipientId
      .flatMap { recipientId ->
        messageRequestRepository.blockAndReportSpamMessageRequest(recipientId, threadId)
      }
      .observeOn(AndroidSchedulers.mainThread())
  }
}
