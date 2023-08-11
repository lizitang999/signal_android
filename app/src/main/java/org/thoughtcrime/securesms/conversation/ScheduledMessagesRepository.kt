package org.mycrimes.insecuretests.conversation

import android.content.Context
import androidx.annotation.WorkerThread
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.mycrimes.insecuretests.conversation.v2.data.AttachmentHelper
import org.mycrimes.insecuretests.database.DatabaseObserver
import org.mycrimes.insecuretests.database.SignalDatabase
import org.mycrimes.insecuretests.database.model.MessageRecord
import org.mycrimes.insecuretests.dependencies.ApplicationDependencies
import org.mycrimes.insecuretests.recipients.Recipient

/**
 * Handles retrieving scheduled messages data to be shown in [ScheduledMessagesBottomSheet] and [ConversationParentFragment]
 */
class ScheduledMessagesRepository {

  /**
   * Get all the scheduled messages for the specified thread, ordered by scheduled time
   */
  fun getScheduledMessages(context: Context, threadId: Long): Observable<List<ConversationMessage>> {
    return Observable.create { emitter ->
      val databaseObserver: DatabaseObserver = ApplicationDependencies.getDatabaseObserver()
      val observer = DatabaseObserver.Observer { emitter.onNext(getScheduledMessagesSync(context, threadId)) }

      databaseObserver.registerScheduledMessageObserver(threadId, observer)

      emitter.setCancellable { databaseObserver.unregisterObserver(observer) }
      emitter.onNext(getScheduledMessagesSync(context, threadId))
    }.subscribeOn(Schedulers.io())
  }

  @WorkerThread
  private fun getScheduledMessagesSync(context: Context, threadId: Long): List<ConversationMessage> {
    var scheduledMessages: List<MessageRecord> = SignalDatabase.messages.getScheduledMessagesInThread(threadId)
    val threadRecipient: Recipient = requireNotNull(SignalDatabase.threads.getRecipientForThreadId(threadId))

    val attachmentHelper = AttachmentHelper()

    attachmentHelper.addAll(scheduledMessages)

    attachmentHelper.fetchAttachments()

    scheduledMessages = attachmentHelper.buildUpdatedModels(ApplicationDependencies.getApplication(), scheduledMessages)

    val replies: List<ConversationMessage> = scheduledMessages
      .map { ConversationMessage.ConversationMessageFactory.createWithUnresolvedData(context, it, threadRecipient) }

    return replies
  }

  /**
   * Get the number of scheduled messages for a given thread
   */
  fun getScheduledMessageCount(threadId: Long): Observable<Int> {
    return Observable.create { emitter ->
      val databaseObserver: DatabaseObserver = ApplicationDependencies.getDatabaseObserver()
      val observer = DatabaseObserver.Observer { emitter.onNext(SignalDatabase.messages.getScheduledMessageCountForThread(threadId)) }

      databaseObserver.registerScheduledMessageObserver(threadId, observer)

      emitter.setCancellable { databaseObserver.unregisterObserver(observer) }
      emitter.onNext(SignalDatabase.messages.getScheduledMessageCountForThread(threadId))
    }.subscribeOn(Schedulers.io())
  }

  fun rescheduleMessage(threadId: Long, messageId: Long, scheduleTime: Long) {
    SignalDatabase.messages.rescheduleMessage(threadId, messageId, scheduleTime)
  }
}
