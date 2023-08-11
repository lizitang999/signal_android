package org.mycrimes.insecuretests.conversation.quotes

import android.app.Application
import androidx.annotation.WorkerThread
import io.reactivex.rxjava3.core.Observable
import org.signal.core.util.logging.Log
import org.mycrimes.insecuretests.conversation.ConversationMessage
import org.mycrimes.insecuretests.conversation.ConversationMessage.ConversationMessageFactory
import org.mycrimes.insecuretests.conversation.v2.data.AttachmentHelper
import org.mycrimes.insecuretests.conversation.v2.data.ReactionHelper
import org.mycrimes.insecuretests.database.DatabaseObserver
import org.mycrimes.insecuretests.database.SignalDatabase
import org.mycrimes.insecuretests.database.model.MediaMmsMessageRecord
import org.mycrimes.insecuretests.database.model.MessageId
import org.mycrimes.insecuretests.database.model.MessageRecord
import org.mycrimes.insecuretests.database.model.Quote
import org.mycrimes.insecuretests.dependencies.ApplicationDependencies
import org.mycrimes.insecuretests.util.getQuote

class MessageQuotesRepository {

  companion object {
    private val TAG = Log.tag(MessageQuotesRepository::class.java)
  }

  /**
   * Retrieves all messages that quote the target message, as well as any messages that quote _those_ messages, recursively.
   */
  fun getMessagesInQuoteChain(application: Application, messageId: MessageId): Observable<List<ConversationMessage>> {
    return Observable.create { emitter ->
      val threadId: Long = SignalDatabase.messages.getThreadIdForMessage(messageId.id)
      if (threadId < 0) {
        Log.w(TAG, "Could not find a threadId for $messageId!")
        emitter.onNext(emptyList())
        return@create
      }

      val databaseObserver: DatabaseObserver = ApplicationDependencies.getDatabaseObserver()
      val observer = DatabaseObserver.Observer { emitter.onNext(getMessagesInQuoteChainSync(application, messageId)) }

      databaseObserver.registerConversationObserver(threadId, observer)

      emitter.setCancellable { databaseObserver.unregisterObserver(observer) }
      emitter.onNext(getMessagesInQuoteChainSync(application, messageId))
    }
  }

  @WorkerThread
  private fun getMessagesInQuoteChainSync(application: Application, messageId: MessageId): List<ConversationMessage> {
    val rootMessageId: MessageId = SignalDatabase.messages.getRootOfQuoteChain(messageId)

    var originalRecord: MessageRecord? = SignalDatabase.messages.getMessageRecordOrNull(rootMessageId.id)

    if (originalRecord == null) {
      return emptyList()
    }

    var replyRecords: List<MessageRecord> = SignalDatabase.messages.getAllMessagesThatQuote(rootMessageId)

    val reactionHelper = ReactionHelper()
    val attachmentHelper = AttachmentHelper()
    val threadRecipient = requireNotNull(SignalDatabase.threads.getRecipientForThreadId(originalRecord.threadId))

    reactionHelper.addAll(replyRecords)
    attachmentHelper.addAll(replyRecords)

    reactionHelper.fetchReactions()
    attachmentHelper.fetchAttachments()

    replyRecords = reactionHelper.buildUpdatedModels(replyRecords)
    replyRecords = attachmentHelper.buildUpdatedModels(ApplicationDependencies.getApplication(), replyRecords)

    val replies: List<ConversationMessage> = replyRecords
      .map { replyRecord ->
        val replyQuote: Quote? = replyRecord.getQuote()
        if (replyQuote != null && replyQuote.id == originalRecord!!.dateSent) {
          (replyRecord as MediaMmsMessageRecord).withoutQuote()
        } else {
          replyRecord
        }
      }
      .map { ConversationMessageFactory.createWithUnresolvedData(application, it, threadRecipient) }

    if (originalRecord.isPaymentNotification) {
      originalRecord = SignalDatabase.payments.updateMessageWithPayment(originalRecord)
    }

    originalRecord = ReactionHelper()
      .apply {
        add(originalRecord)
        fetchReactions()
      }
      .buildUpdatedModels(listOf(originalRecord))
      .get(0)

    originalRecord = AttachmentHelper()
      .apply {
        add(originalRecord)
        fetchAttachments()
      }
      .buildUpdatedModels(ApplicationDependencies.getApplication(), listOf(originalRecord))
      .get(0)

    val originalMessage: ConversationMessage = ConversationMessageFactory.createWithUnresolvedData(application, originalRecord, false, threadRecipient)

    return replies + originalMessage
  }
}
