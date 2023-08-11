package org.mycrimes.insecuretests.contacts.avatars;

import android.content.Context;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

import org.mycrimes.insecuretests.conversation.colors.AvatarColor;

public interface FallbackContactPhoto {

 Drawable asDrawable(@NonNull Context context, @NonNull AvatarColor color);
 Drawable asDrawable(@NonNull Context context, @NonNull AvatarColor color, boolean inverted);
 Drawable asSmallDrawable(@NonNull Context context, @NonNull AvatarColor color, boolean inverted);
 Drawable asCallCard(@NonNull Context context);

}
