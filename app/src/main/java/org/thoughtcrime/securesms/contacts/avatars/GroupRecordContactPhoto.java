package org.mycrimes.insecuretests.contacts.avatars;


import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.Conversions;
import org.mycrimes.insecuretests.database.GroupTable;
import org.mycrimes.insecuretests.database.SignalDatabase;
import org.mycrimes.insecuretests.database.model.GroupRecord;
import org.mycrimes.insecuretests.groups.GroupId;
import org.mycrimes.insecuretests.profiles.AvatarHelper;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Optional;

public final class GroupRecordContactPhoto implements ContactPhoto {

  private final GroupId groupId;
  private final long    avatarId;

  public GroupRecordContactPhoto(@NonNull GroupId groupId, long avatarId) {
    this.groupId  = groupId;
    this.avatarId = avatarId;
  }

  @Override
  public InputStream openInputStream(Context context) throws IOException {
    GroupTable            groupDatabase = SignalDatabase.groups();
    Optional<GroupRecord> groupRecord   = groupDatabase.getGroup(groupId);

    if (!groupRecord.isPresent() || !AvatarHelper.hasAvatar(context, groupRecord.get().getRecipientId())) {
      throw new IOException("No avatar for group: " + groupId);
    }

    return AvatarHelper.getAvatar(context, groupRecord.get().getRecipientId());
  }

  @Override
  public @Nullable Uri getUri(@NonNull Context context) {
    return null;
  }

  @Override
  public boolean isProfilePhoto() {
    return false;
  }

  @Override
  public void updateDiskCacheKey(@NonNull MessageDigest messageDigest) {
    messageDigest.update(groupId.toString().getBytes());
    messageDigest.update(Conversions.longToByteArray(avatarId));
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof GroupRecordContactPhoto)) return false;

    GroupRecordContactPhoto that = (GroupRecordContactPhoto)other;
    return this.groupId.equals(that.groupId) && this.avatarId == that.avatarId;
  }

  @Override
  public int hashCode() {
    return this.groupId.hashCode() ^ (int) avatarId;
  }
}
