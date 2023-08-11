package org.mycrimes.insecuretests.jobs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.mycrimes.insecuretests.attachments.AttachmentId;
import org.mycrimes.insecuretests.attachments.DatabaseAttachment;
import org.mycrimes.insecuretests.database.AttachmentTable;
import org.mycrimes.insecuretests.database.SignalDatabase;
import org.mycrimes.insecuretests.jobmanager.JsonJobData;
import org.mycrimes.insecuretests.jobmanager.Job;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Only marks an attachment as uploaded.
 */
public final class AttachmentMarkUploadedJob extends BaseJob {

  public static final String KEY = "AttachmentMarkUploadedJob";

  @SuppressWarnings("unused")
  private static final String TAG = Log.tag(AttachmentMarkUploadedJob.class);

  private static final String KEY_ROW_ID     = "row_id";
  private static final String KEY_UNIQUE_ID  = "unique_id";
  private static final String KEY_MESSAGE_ID = "message_id";

  private final AttachmentId attachmentId;
  private final long         messageId;

  public AttachmentMarkUploadedJob(long messageId, @NonNull AttachmentId attachmentId) {
    this(new Parameters.Builder()
                       .setLifespan(TimeUnit.DAYS.toMillis(1))
                       .setMaxAttempts(Parameters.UNLIMITED)
                       .build(),
         messageId,
         attachmentId);
  }

  private AttachmentMarkUploadedJob(@NonNull Parameters parameters, long messageId, @NonNull AttachmentId attachmentId) {
    super(parameters);
    this.attachmentId = attachmentId;
    this.messageId    = messageId;
  }

  @Override
  public @Nullable byte[] serialize() {
    return new JsonJobData.Builder().putLong(KEY_ROW_ID, attachmentId.getRowId())
                                    .putLong(KEY_UNIQUE_ID, attachmentId.getUniqueId())
                                    .putLong(KEY_MESSAGE_ID, messageId)
                                    .serialize();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onRun() throws Exception {
    AttachmentTable    database           = SignalDatabase.attachments();
    DatabaseAttachment databaseAttachment = database.getAttachment(attachmentId);

    if (databaseAttachment == null) {
      throw new InvalidAttachmentException("Cannot find the specified attachment.");
    }

    database.markAttachmentUploaded(messageId, databaseAttachment);
  }

  @Override
  public void onFailure() {
  }

  @Override
  protected boolean onShouldRetry(@NonNull Exception exception) {
    return exception instanceof IOException;
  }

  private class InvalidAttachmentException extends Exception {
    InvalidAttachmentException(String message) {
      super(message);
    }
  }

  public static final class Factory implements Job.Factory<AttachmentMarkUploadedJob> {
    @Override
    public @NonNull AttachmentMarkUploadedJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      JsonJobData data = JsonJobData.deserialize(serializedData);

      return new AttachmentMarkUploadedJob(parameters,
                                           data.getLong(KEY_MESSAGE_ID),
                                           new AttachmentId(data.getLong(KEY_ROW_ID), data.getLong(KEY_UNIQUE_ID)));
    }
  }
}
