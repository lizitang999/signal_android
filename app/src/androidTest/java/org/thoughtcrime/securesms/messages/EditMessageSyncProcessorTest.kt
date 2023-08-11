package org.mycrimes.insecuretests.messages

import android.database.Cursor
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.signal.core.util.ThreadUtil
import org.signal.core.util.readToList
import org.signal.core.util.select
import org.signal.core.util.withinTransaction
import org.mycrimes.insecuretests.database.AttachmentTable
import org.mycrimes.insecuretests.database.MessageTable
import org.mycrimes.insecuretests.database.SignalDatabase
import org.mycrimes.insecuretests.database.ThreadTable
import org.mycrimes.insecuretests.database.model.toBodyRangeList
import org.mycrimes.insecuretests.mms.OutgoingMessage
import org.mycrimes.insecuretests.recipients.Recipient
import org.mycrimes.insecuretests.testing.MessageContentFuzzer
import org.mycrimes.insecuretests.testing.SignalActivityRule
import org.mycrimes.insecuretests.testing.assertIs
import org.mycrimes.insecuretests.util.MessageTableTestUtils
import org.whispersystems.signalservice.internal.push.SignalServiceProtos
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.EditMessage
import kotlin.time.Duration.Companion.seconds

@RunWith(AndroidJUnit4::class)
class EditMessageSyncProcessorTest {

  companion object {
    private val IGNORE_MESSAGE_COLUMNS = listOf(
      MessageTable.DATE_RECEIVED,
      MessageTable.NOTIFIED_TIMESTAMP,
      MessageTable.REACTIONS_LAST_SEEN,
      MessageTable.NOTIFIED
    )

    private val IGNORE_ATTACHMENT_COLUMNS = listOf(
      AttachmentTable.UNIQUE_ID,
      AttachmentTable.TRANSFER_FILE
    )
  }

  @get:Rule
  val harness = SignalActivityRule()

  private lateinit var processorV2: MessageContentProcessorV2
  private lateinit var testResult: TestResults
  private var envelopeTimestamp: Long = 0

  @Before
  fun setup() {
    processorV2 = MessageContentProcessorV2(harness.context)
    envelopeTimestamp = System.currentTimeMillis()
    testResult = TestResults()
  }

  @Test
  fun textMessage() {
    var originalTimestamp = envelopeTimestamp + 200
    for (i in 1..10) {
      originalTimestamp += 400

      val toRecipient = Recipient.resolved(harness.others[0])

      val content = MessageContentFuzzer.fuzzTextMessage()
      val metadata = MessageContentFuzzer.envelopeMetadata(harness.self.id, toRecipient.id)
      val syncContent = SignalServiceProtos.Content.newBuilder().setSyncMessage(
        SignalServiceProtos.SyncMessage.newBuilder().setSent(
          SignalServiceProtos.SyncMessage.Sent.newBuilder()
            .setDestinationUuid(metadata.destinationServiceId.toString())
            .setTimestamp(originalTimestamp)
            .setExpirationStartTimestamp(originalTimestamp)
            .setMessage(content.dataMessage)
        )
      ).build()
      SignalDatabase.recipients.setExpireMessages(toRecipient.id, content.dataMessage.expireTimer)
      val syncTextMessage = TestMessage(
        envelope = MessageContentFuzzer.envelope(originalTimestamp),
        content = syncContent,
        metadata = metadata,
        serverDeliveredTimestamp = MessageContentFuzzer.fuzzServerDeliveredTimestamp(originalTimestamp)
      )

      val editTimestamp = originalTimestamp + 200
      val editedContent = MessageContentFuzzer.fuzzTextMessage()
      val editSyncContent = SignalServiceProtos.Content.newBuilder().setSyncMessage(
        SignalServiceProtos.SyncMessage.newBuilder().setSent(
          SignalServiceProtos.SyncMessage.Sent.newBuilder()
            .setDestinationUuid(metadata.destinationServiceId.toString())
            .setTimestamp(editTimestamp)
            .setExpirationStartTimestamp(editTimestamp)
            .setEditMessage(
              EditMessage.newBuilder()
                .setDataMessage(editedContent.dataMessage)
                .setTargetSentTimestamp(originalTimestamp)
            )
        )
      ).build()

      val syncEditMessage = TestMessage(
        envelope = MessageContentFuzzer.envelope(editTimestamp),
        content = editSyncContent,
        metadata = metadata,
        serverDeliveredTimestamp = MessageContentFuzzer.fuzzServerDeliveredTimestamp(editTimestamp)
      )

      testResult.runSync(listOf(syncTextMessage, syncEditMessage))

      SignalDatabase.recipients.setExpireMessages(toRecipient.id, content.dataMessage.expireTimer / 1000)
      val originalTextMessage = OutgoingMessage(
        threadRecipient = toRecipient,
        sentTimeMillis = originalTimestamp,
        body = content.dataMessage.body,
        expiresIn = content.dataMessage.expireTimer.seconds.inWholeMilliseconds,
        isUrgent = true,
        isSecure = true,
        bodyRanges = content.dataMessage.bodyRangesList.toBodyRangeList()
      )
      val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(toRecipient)
      val originalMessageId = SignalDatabase.messages.insertMessageOutbox(originalTextMessage, threadId, false, null)
      SignalDatabase.messages.markAsSent(originalMessageId, true)
      if (content.dataMessage.expireTimer > 0) {
        SignalDatabase.messages.markExpireStarted(originalMessageId, originalTimestamp)
      }

      val editMessage = OutgoingMessage(
        threadRecipient = toRecipient,
        sentTimeMillis = editTimestamp,
        body = editedContent.dataMessage.body,
        expiresIn = content.dataMessage.expireTimer.seconds.inWholeMilliseconds,
        isUrgent = true,
        isSecure = true,
        bodyRanges = editedContent.dataMessage.bodyRangesList.toBodyRangeList(),
        messageToEdit = originalMessageId
      )

      val editMessageId = SignalDatabase.messages.insertMessageOutbox(editMessage, threadId, false, null)
      SignalDatabase.messages.markAsSent(editMessageId, true)

      if (content.dataMessage.expireTimer > 0) {
        SignalDatabase.messages.markExpireStarted(editMessageId, originalTimestamp)
      }
      testResult.collectLocal()
      testResult.assert()
    }
  }

  private inner class TestResults {

    private lateinit var localMessages: List<List<Pair<String, String?>>>
    private lateinit var localAttachments: List<List<Pair<String, String?>>>

    private lateinit var syncMessages: List<List<Pair<String, String?>>>
    private lateinit var syncAttachments: List<List<Pair<String, String?>>>

    fun collectLocal() {
      harness.inMemoryLogger.clear()

      localMessages = dumpMessages()
      localAttachments = dumpAttachments()

      cleanup()
    }

    fun runSync(messages: List<TestMessage>) {
      messages.forEach { (envelope, content, metadata, serverDeliveredTimestamp) ->
        if (content.hasSyncMessage()) {
          processorV2.process(
            envelope,
            content,
            metadata,
            serverDeliveredTimestamp,
            false
          )
          ThreadUtil.sleep(1)
        }
      }
      harness.inMemoryLogger.clear()

      syncMessages = dumpMessages()
      syncAttachments = dumpAttachments()

      cleanup()
    }

    fun cleanup() {
      SignalDatabase.rawDatabase.withinTransaction { db ->
        SignalDatabase.threads.deleteAllConversations()
        db.execSQL("DELETE FROM sqlite_sequence WHERE name = '${MessageTable.TABLE_NAME}'")
        db.execSQL("DELETE FROM sqlite_sequence WHERE name = '${ThreadTable.TABLE_NAME}'")
        db.execSQL("DELETE FROM sqlite_sequence WHERE name = '${AttachmentTable.TABLE_NAME}'")
      }
    }

    fun assert() {
      syncMessages.zip(localMessages)
        .forEach { (v2, v1) ->
          v2.assertIs(v1)
        }

      syncAttachments.zip(localAttachments)
        .forEach { (v2, v1) ->
          v2.assertIs(v1)
        }
    }

    private fun dumpMessages(): List<List<Pair<String, String?>>> {
      return dumpTable(MessageTable.TABLE_NAME)
        .map { row ->
          val newRow = row.toMutableList()
          newRow.removeIf { IGNORE_MESSAGE_COLUMNS.contains(it.first) }
          newRow
        }
    }

    private fun dumpAttachments(): List<List<Pair<String, String?>>> {
      return dumpTable(AttachmentTable.TABLE_NAME)
        .map { row ->
          val newRow = row.toMutableList()
          newRow.removeIf { IGNORE_ATTACHMENT_COLUMNS.contains(it.first) }
          newRow
        }
    }

    private fun dumpTable(table: String): List<List<Pair<String, String?>>> {
      return SignalDatabase.rawDatabase
        .select()
        .from(table)
        .run()
        .readToList { cursor ->
          val map: List<Pair<String, String?>> = cursor.columnNames.map { column ->
            val index = cursor.getColumnIndex(column)
            var data: String? = when (cursor.getType(index)) {
              Cursor.FIELD_TYPE_BLOB -> Base64.encodeToString(cursor.getBlob(index), 0)
              else -> cursor.getString(index)
            }
            if (table == MessageTable.TABLE_NAME && column == MessageTable.TYPE) {
              data = MessageTableTestUtils.typeColumnToString(cursor.getLong(index))
            }

            column to data
          }
          map
        }
    }
  }
}
