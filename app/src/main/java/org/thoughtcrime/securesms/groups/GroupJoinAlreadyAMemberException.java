package org.mycrimes.insecuretests.groups;

import androidx.annotation.NonNull;

public final class GroupJoinAlreadyAMemberException extends GroupChangeException {

  GroupJoinAlreadyAMemberException(@NonNull Throwable throwable) {
    super(throwable);
  }
}
