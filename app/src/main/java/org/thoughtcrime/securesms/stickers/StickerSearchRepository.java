package org.mycrimes.insecuretests.stickers;

import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;

import org.signal.core.util.concurrent.SignalExecutors;
import org.mycrimes.insecuretests.components.emoji.EmojiUtil;
import org.mycrimes.insecuretests.database.AttachmentTable;
import org.mycrimes.insecuretests.database.SignalDatabase;
import org.mycrimes.insecuretests.database.StickerTable;
import org.mycrimes.insecuretests.database.StickerTable.StickerRecordReader;
import org.mycrimes.insecuretests.database.model.StickerRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class StickerSearchRepository {

  private final StickerTable    stickerDatabase;
  private final AttachmentTable attachmentDatabase;

  public StickerSearchRepository(@NonNull Context context) {
    this.stickerDatabase    = SignalDatabase.stickers();
    this.attachmentDatabase = SignalDatabase.attachments();
  }

  public void searchByEmoji(@NonNull String emoji, @NonNull Callback<List<StickerRecord>> callback) {
    SignalExecutors.BOUNDED.execute(() -> {
      String              searchEmoji = EmojiUtil.getCanonicalRepresentation(emoji);
      List<StickerRecord> out         = new ArrayList<>();
      Set<String>         possible    = EmojiUtil.getAllRepresentations(searchEmoji);

      for (String candidate : possible) {
        try (StickerRecordReader reader = new StickerRecordReader(stickerDatabase.getStickersByEmoji(candidate))) {
          StickerRecord record = null;
          while ((record = reader.getNext()) != null) {
            out.add(record);
          }
        }
      }

      callback.onResult(out);
    });
  }

  public void getStickerFeatureAvailability(@NonNull Callback<Boolean> callback) {
    SignalExecutors.BOUNDED.execute(() -> {
      try (Cursor cursor = stickerDatabase.getAllStickerPacks("1")) {
        if (cursor != null && cursor.moveToFirst()) {
          callback.onResult(true);
        } else {
          callback.onResult(attachmentDatabase.hasStickerAttachments());
        }
      }
    });
  }

  public interface Callback<T> {
    void onResult(T result);
  }
}
