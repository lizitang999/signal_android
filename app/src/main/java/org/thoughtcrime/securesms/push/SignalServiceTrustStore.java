package org.mycrimes.insecuretests.push;

import android.content.Context;

import org.mycrimes.insecuretests.R;
import org.whispersystems.signalservice.api.push.TrustStore;

import java.io.InputStream;

public class SignalServiceTrustStore implements TrustStore {

  private final Context context;

  public SignalServiceTrustStore(Context context) {
    this.context = context.getApplicationContext();
  }

  @Override
  public InputStream getKeyStoreInputStream() {
    return context.getResources().openRawResource(R.raw.whisper);
  }

  @Override
  public String getKeyStorePassword() {
    return "whisper";
  }
}
