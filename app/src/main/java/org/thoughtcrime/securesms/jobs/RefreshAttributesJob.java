package org.mycrimes.insecuretests.jobs;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.mycrimes.insecuretests.AppCapabilities;
import org.mycrimes.insecuretests.crypto.ProfileKeyUtil;
import org.mycrimes.insecuretests.dependencies.ApplicationDependencies;
import org.mycrimes.insecuretests.jobmanager.JsonJobData;
import org.mycrimes.insecuretests.jobmanager.Job;
import org.mycrimes.insecuretests.jobmanager.impl.NetworkConstraint;
import org.mycrimes.insecuretests.keyvalue.KbsValues;
import org.mycrimes.insecuretests.keyvalue.SignalStore;
import org.mycrimes.insecuretests.registration.RegistrationRepository;
import org.mycrimes.insecuretests.registration.secondary.DeviceNameCipher;
import org.mycrimes.insecuretests.util.TextSecurePreferences;
import org.whispersystems.signalservice.api.account.AccountAttributes;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess;
import org.whispersystems.signalservice.api.push.exceptions.NetworkFailureException;
import org.whispersystems.util.Base64;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public class RefreshAttributesJob extends BaseJob {

  public static final String KEY = "RefreshAttributesJob";

  private static final String TAG = Log.tag(RefreshAttributesJob.class);

  private static final String KEY_FORCED = "forced";

  private static volatile boolean hasRefreshedThisAppCycle;

  private final boolean forced;

  public RefreshAttributesJob() {
    this(true);
  }

  /**
   * @param forced True if you want this job to run no matter what. False if you only want this job
   *               to run if it hasn't run yet this app cycle.
   */
  public RefreshAttributesJob(boolean forced) {
    this(new Job.Parameters.Builder()
                           .addConstraint(NetworkConstraint.KEY)
                           .setQueue("RefreshAttributesJob")
                           .setMaxInstancesForFactory(2)
                           .setLifespan(TimeUnit.DAYS.toMillis(30))
                           .setMaxAttempts(Parameters.UNLIMITED)
                           .build(),
         forced);
  }

  private RefreshAttributesJob(@NonNull Job.Parameters parameters, boolean forced) {
    super(parameters);
    this.forced = forced;
  }

  @Override
  public @Nullable byte[] serialize() {
    return new JsonJobData.Builder().putBoolean(KEY_FORCED, forced).serialize();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onRun() throws IOException {
    if (!SignalStore.account().isRegistered() || SignalStore.account().getE164() == null) {
      Log.w(TAG, "Not yet registered. Skipping.");
      return;
    }

    if (!forced && hasRefreshedThisAppCycle) {
      Log.d(TAG, "Already refreshed this app cycle. Skipping.");
      return;
    }

    int       registrationId              = SignalStore.account().getRegistrationId();
    boolean   fetchesMessages             = !SignalStore.account().isFcmEnabled() || SignalStore.internalValues().isWebsocketModeForced();
    byte[]    unidentifiedAccessKey       = UnidentifiedAccess.deriveAccessKeyFrom(ProfileKeyUtil.getSelfProfileKey());
    boolean   universalUnidentifiedAccess = TextSecurePreferences.isUniversalUnidentifiedAccess(context);
    String    registrationLockV1          = null;
    String    registrationLockV2          = null;
    KbsValues kbsValues                   = SignalStore.kbsValues();
    int       pniRegistrationId           = new RegistrationRepository(ApplicationDependencies.getApplication()).getPniRegistrationId();
    String    recoveryPassword            = kbsValues.getRecoveryPassword();

    if (kbsValues.isV2RegistrationLockEnabled()) {
      registrationLockV2 = kbsValues.getRegistrationLockToken();
    } else if (TextSecurePreferences.isV1RegistrationLockEnabled(context)) {
      //noinspection deprecation Ok to read here as they have not migrated
      registrationLockV1 = TextSecurePreferences.getDeprecatedV1RegistrationLockPin(context);
    }

    boolean phoneNumberDiscoverable = SignalStore.phoneNumberPrivacy().getPhoneNumberListingMode().isDiscoverable();

    String deviceName = SignalStore.account().getDeviceName();
    byte[] encryptedDeviceName = (deviceName == null) ? null : DeviceNameCipher.encryptDeviceName(deviceName.getBytes(StandardCharsets.UTF_8), SignalStore.account().getAciIdentityKey());

    AccountAttributes.Capabilities capabilities = AppCapabilities.getCapabilities(kbsValues.hasPin() && !kbsValues.hasOptedOut());
    Log.i(TAG, "Calling setAccountAttributes() reglockV1? " + !TextUtils.isEmpty(registrationLockV1) + ", reglockV2? " + !TextUtils.isEmpty(registrationLockV2) + ", pin? " + kbsValues.hasPin() +
               "\n    Recovery password? " + !TextUtils.isEmpty(recoveryPassword) +
               "\n    Phone number discoverable : " + phoneNumberDiscoverable +
               "\n    Device Name : " + (encryptedDeviceName != null) +
               "\n  Capabilities: " + capabilities);

    AccountAttributes accountAttributes = new AccountAttributes(
        null,
        registrationId,
        fetchesMessages,
        registrationLockV1,
        registrationLockV2,
        unidentifiedAccessKey,
        universalUnidentifiedAccess,
        capabilities,
        phoneNumberDiscoverable,
        (encryptedDeviceName == null) ? null : Base64.encodeBytes(encryptedDeviceName),
        pniRegistrationId,
        recoveryPassword
    );

    ApplicationDependencies.getSignalServiceAccountManager().setAccountAttributes(accountAttributes);

    hasRefreshedThisAppCycle = true;
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception e) {
    return e instanceof NetworkFailureException;
  }

  @Override
  public void onFailure() {
    Log.w(TAG, "Failed to update account attributes!");
  }

  public static class Factory implements Job.Factory<RefreshAttributesJob> {
    @Override
    public @NonNull RefreshAttributesJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      JsonJobData data = JsonJobData.deserialize(serializedData);
      return new RefreshAttributesJob(parameters, data.getBooleanOrDefault(KEY_FORCED, true));
    }
  }
}
