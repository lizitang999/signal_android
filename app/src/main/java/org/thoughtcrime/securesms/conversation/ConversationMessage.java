package org.mycrimes.insecuretests.conversation;

import android.content.Context;
import android.text.SpannableString;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.signal.core.util.Conversions;
import org.mycrimes.insecuretests.components.mention.MentionAnnotation;
import org.mycrimes.insecuretests.conversation.mutiselect.Multiselect;
import org.mycrimes.insecuretests.conversation.mutiselect.MultiselectCollection;
import org.mycrimes.insecuretests.database.BodyRangeUtil;
import org.mycrimes.insecuretests.database.MentionUtil;
import org.mycrimes.insecuretests.database.SignalDatabase;
import org.mycrimes.insecuretests.database.model.Mention;
import org.mycrimes.insecuretests.database.model.MessageRecord;
import org.mycrimes.insecuretests.database.model.databaseprotos.BodyRangeList;
import org.mycrimes.insecuretests.recipients.Recipient;
import org.mycrimes.insecuretests.util.MessageRecordUtil;

import java.security.MessageDigest;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A view level model used to pass arbitrary message related information needed
 * for various presentations.
 */
public class ConversationMessage {
  @NonNull  private final MessageRecord          messageRecord;
  @NonNull  private final List<Mention>          mentions;
  @Nullable private final SpannableString        body;
  @NonNull  private final MultiselectCollection  multiselectCollection;
  @NonNull  private final MessageStyler.Result   styleResult;
  @NonNull  private final Recipient              threadRecipient;
            private final boolean                hasBeenQuoted;

  private ConversationMessage(@NonNull MessageRecord messageRecord,
                              @Nullable CharSequence body,
                              @Nullable List<Mention> mentions,
                              boolean hasBeenQuoted,
                              @Nullable MessageStyler.Result styleResult,
                              @NonNull Recipient threadRecipient)
  {
    this.messageRecord   = messageRecord;
    this.hasBeenQuoted   = hasBeenQuoted;
    this.mentions        = mentions != null ? mentions : Collections.emptyList();
    this.styleResult     = styleResult != null ? styleResult : MessageStyler.Result.none();
    this.threadRecipient = threadRecipient;

    if (body != null) {
      this.body = SpannableString.valueOf(body);
    } else if (messageRecord.getMessageRanges() != null) {
      this.body = SpannableString.valueOf(messageRecord.getBody());
    } else {
      this.body = null;
    }

    if (!this.mentions.isEmpty() && this.body != null) {
      MentionAnnotation.setMentionAnnotations(this.body, this.mentions);
    }

    multiselectCollection = Multiselect.getParts(this);
  }

  public @NonNull MessageRecord getMessageRecord() {
    return messageRecord;
  }

  public @NonNull List<Mention> getMentions() {
    return mentions;
  }

  public @NonNull MultiselectCollection getMultiselectCollection() {
    return multiselectCollection;
  }

  public boolean hasBeenQuoted() {
    return hasBeenQuoted;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final ConversationMessage that = (ConversationMessage) o;
    return messageRecord.equals(that.messageRecord);
  }

  @Override
  public int hashCode() {
    return messageRecord.hashCode();
  }

  public long getUniqueId(@NonNull MessageDigest digest) {
    String unique = (messageRecord.isMms() ? "MMS::" : "SMS::") + messageRecord.getId();
    byte[] bytes  = digest.digest(unique.getBytes());

    return Conversions.byteArrayToLong(bytes);
  }

  public @NonNull SpannableString getDisplayBody(Context context) {
    return (body != null) ? new SpannableString(body) : messageRecord.getDisplayBody(context);
  }

  public boolean hasStyleLinks() {
    return styleResult.getHasStyleLinks();
  }

  public @Nullable BodyRangeList.BodyRange.Button getBottomButton() {
    return styleResult.getBottomButton();
  }

  public boolean isTextOnly(@NonNull Context context) {
    return MessageRecordUtil.isTextOnly(messageRecord, context) &&
           !hasBeenQuoted() &&
           getBottomButton() == null;
  }

  public boolean hasBeenScheduled() {
    return MessageRecordUtil.isScheduled(messageRecord);
  }

  @NonNull public Recipient getThreadRecipient() {
    return threadRecipient;
  }

  /**
   * Factory providing multiple ways of creating {@link ConversationMessage}s.
   */
  public static class ConversationMessageFactory {

    /**
     * Creates a {@link ConversationMessage} wrapping the provided MessageRecord and will update and modify the provided
     * mentions from placeholder to actual. This method may perform database operations to resolve mentions to display names.
     *
     * @param mentions List of placeholder mentions to be used to update the body in the provided MessageRecord.
     */
    @WorkerThread
    public static @NonNull ConversationMessage createWithUnresolvedData(@NonNull Context context,
                                                                        @NonNull MessageRecord messageRecord,
                                                                        @NonNull CharSequence body,
                                                                        @Nullable List<Mention> mentions,
                                                                        boolean hasBeenQuoted,
                                                                        @NonNull Recipient threadRecipient)
    {
      SpannableString      styledAndMentionBody = null;
      MessageStyler.Result styleResult          = MessageStyler.Result.none();

      MentionUtil.UpdatedBodyAndMentions mentionsUpdate = null;
      if (mentions != null && !mentions.isEmpty()) {
        mentionsUpdate = MentionUtil.updateBodyAndMentionsWithDisplayNames(context, body, mentions);
      }

      if (messageRecord.getMessageRanges() != null) {
        BodyRangeList bodyRanges = mentionsUpdate == null ? messageRecord.getMessageRanges()
                                                          : BodyRangeUtil.adjustBodyRanges(messageRecord.getMessageRanges(), mentionsUpdate.getBodyAdjustments());

        styledAndMentionBody = SpannableString.valueOf(mentionsUpdate != null ? mentionsUpdate.getBody() : body);
        styleResult          = MessageStyler.style(messageRecord.getDateSent(), bodyRanges, styledAndMentionBody);
      }

      return new ConversationMessage(messageRecord,
                                     styledAndMentionBody != null ? styledAndMentionBody : mentionsUpdate != null ? mentionsUpdate.getBody() : body,
                                     mentionsUpdate != null ? mentionsUpdate.getMentions() : null,
                                     hasBeenQuoted,
                                     styleResult,
                                     threadRecipient);
    }

    /**
     * Creates a {@link ConversationMessage} wrapping the provided MessageRecord, and will query for potential mentions. If mentions
     * are found, the body of the provided message will be updated and modified to match actual mentions. This will perform
     * database operations to query for mentions and then to resolve mentions to display names.
     */
    @WorkerThread
    public static @NonNull ConversationMessage createWithUnresolvedData(@NonNull Context context, @NonNull MessageRecord messageRecord, @NonNull Recipient threadRecipient) {
      return createWithUnresolvedData(context, messageRecord, messageRecord.getDisplayBody(context), threadRecipient);
    }

    /**
     * Creates a {@link ConversationMessage} wrapping the provided MessageRecord and body, and will query for potential mentions. If mentions
     * are found, the body of the provided message will be updated and modified to match actual mentions. This will perform
     * database operations to query for mentions and then to resolve mentions to display names.
     */
    @WorkerThread
    public static @NonNull ConversationMessage createWithUnresolvedData(@NonNull Context context, @NonNull MessageRecord messageRecord, boolean hasBeenQuoted, @NonNull Recipient threadRecipient) {
      List<Mention> mentions = messageRecord.isMms() ? SignalDatabase.mentions().getMentionsForMessage(messageRecord.getId())
                                                     : null;
      return createWithUnresolvedData(context, messageRecord, messageRecord.getDisplayBody(context), mentions, hasBeenQuoted, threadRecipient);
    }

    /**
     * Creates a {@link ConversationMessage} wrapping the provided MessageRecord and body, and will query for potential mentions. If mentions
     * are found, the body of the provided message will be updated and modified to match actual mentions. This will perform
     * database operations to query for mentions and then to resolve mentions to display names.
     */
    @WorkerThread
    public static @NonNull ConversationMessage createWithUnresolvedData(@NonNull Context context, @NonNull MessageRecord messageRecord, @NonNull CharSequence body, @NonNull Recipient threadRecipient) {
      boolean       hasBeenQuoted = SignalDatabase.messages().isQuoted(messageRecord);
      List<Mention> mentions      = SignalDatabase.mentions().getMentionsForMessage(messageRecord.getId());

      return createWithUnresolvedData(context, messageRecord, body, mentions, hasBeenQuoted, threadRecipient);
    }
  }
}
