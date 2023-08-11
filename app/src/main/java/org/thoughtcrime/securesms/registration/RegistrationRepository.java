package org.mycrimes.insecuretests.registration;

import android.app.Application;
import android.app.backup.BackupManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.core.app.NotificationManagerCompat;

import org.signal.core.util.logging.Log;
import org.signal.libsignal.protocol.state.KyberPreKeyRecord;
import org.signal.libsignal.protocol.state.PreKeyRecord;
import org.signal.libsignal.protocol.state.SignalProtocolStore;
import org.signal.libsignal.protocol.state.SignedPreKeyRecord;
import org.signal.libsignal.protocol.util.KeyHelper;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;
import org.mycrimes.insecuretests.crypto.PreKeyUtil;
import org.mycrimes.insecuretests.crypto.ProfileKeyUtil;
import org.mycrimes.insecuretests.crypto.SenderKeyUtil;
import org.mycrimes.insecuretests.crypto.storage.PreKeyMetadataStore;
import org.mycrimes.insecuretests.crypto.storage.SignalServiceAccountDataStoreImpl;
import org.mycrimes.insecuretests.database.IdentityTable;
import org.mycrimes.insecuretests.database.RecipientTable;
import org.mycrimes.insecuretests.database.SignalDatabase;
import org.mycrimes.insecuretests.dependencies.ApplicationDependencies;
import org.mycrimes.insecuretests.jobmanager.JobManager;
import org.mycrimes.insecuretests.jobs.DirectoryRefreshJob;
import org.mycrimes.insecuretests.jobs.RotateCertificateJob;
import org.mycrimes.insecuretests.keyvalue.SignalStore;
import org.mycrimes.insecuretests.notifications.NotificationIds;
import org.mycrimes.insecuretests.pin.PinState;
import org.mycrimes.insecuretests.push.AccountManagerFactory;
import org.mycrimes.insecuretests.recipients.Recipient;
import org.mycrimes.insecuretests.recipients.RecipientId;
import org.mycrimes.insecuretests.service.DirectoryRefreshListener;
import org.mycrimes.insecuretests.service.RotateSignedPreKeyListener;
import org.mycrimes.insecuretests.util.TextSecurePreferences;
import org.whispersystems.signalservice.api.KbsPinData;
import org.whispersystems.signalservice.api.SignalServiceAccountDataStore;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.account.PreKeyUpload;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.PNI;
import org.whispersystems.signalservice.api.push.ServiceIdType;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.internal.ServiceResponse;
import org.whispersystems.signalservice.internal.push.BackupAuthCheckProcessor;
import org.whispersystems.signalservice.internal.push.VerifyAccountResponse;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Operations required for finalizing the registration of an account. This is
 * to be used after verifying the code and registration lock (if necessary) with
 * the server and being issued a UUID.
 */
public final class RegistrationRepository {

  private static final String TAG = Log.tag(RegistrationRepository.class);

  private final Application context;

  public RegistrationRepository(@NonNull Application context) {
    this.context = context;
  }

  public int getRegistrationId() {
    int registrationId = SignalStore.account().getRegistrationId();
    if (registrationId == 0) {
      registrationId = KeyHelper.generateRegistrationId(false);
      SignalStore.account().setRegistrationId(registrationId);
    }
    return registrationId;
  }

  public int getPniRegistrationId() {
    int pniRegistrationId = SignalStore.account().getPniRegistrationId();
    if (pniRegistrationId == 0) {
      pniRegistrationId = KeyHelper.generateRegistrationId(false);
      SignalStore.account().setPniRegistrationId(pniRegistrationId);
    }
    return pniRegistrationId;
  }

  public @NonNull ProfileKey getProfileKey(@NonNull String e164) {
    ProfileKey profileKey = findExistingProfileKey(e164);

    if (profileKey == null) {
      profileKey = ProfileKeyUtil.createNew();
      Log.i(TAG, "No profile key found, created a new one");
    }

    return profileKey;
  }

  public Single<ServiceResponse<VerifyResponse>> registerAccount(@NonNull RegistrationData registrationData,
                                                                 @NonNull VerifyResponse response,
                                                                 boolean setRegistrationLockEnabled)
  {
    return Single.<ServiceResponse<VerifyResponse>>fromCallable(() -> {
      try {
        String pin = response.getPin();
        registerAccountInternal(registrationData, response.getVerifyAccountResponse(), pin, response.getKbsData(), setRegistrationLockEnabled);

        if (pin != null && !pin.isEmpty()) {
          PinState.onPinChangedOrCreated(context, pin, SignalStore.pinValues().getKeyboardType());
        }

        JobManager jobManager = ApplicationDependencies.getJobManager();
        jobManager.add(new DirectoryRefreshJob(false));
        jobManager.add(new RotateCertificateJob());

        DirectoryRefreshListener.schedule(context);
        RotateSignedPreKeyListener.schedule(context);

        return ServiceResponse.forResult(response, 200, null);
      } catch (IOException e) {
        return ServiceResponse.forUnknownError(e);
      }
    }).subscribeOn(Schedulers.io());
  }

  @WorkerThread
  private void registerAccountInternal(@NonNull RegistrationData registrationData,
                                       @NonNull VerifyAccountResponse response,
                                       @Nullable String pin,
                                       @Nullable KbsPinData kbsData,
                                       boolean setRegistrationLockEnabled)
      throws IOException
  {
    ACI     aci    = ACI.parseOrThrow(response.getUuid());
    PNI     pni    = PNI.parseOrThrow(response.getPni());
    boolean hasPin = response.isStorageCapable();

    SignalStore.account().setAci(aci);
    SignalStore.account().setPni(pni);

    ApplicationDependencies.getProtocolStore().aci().sessions().archiveAllSessions();
    ApplicationDependencies.getProtocolStore().pni().sessions().archiveAllSessions();
    SenderKeyUtil.clearAllState();

    SignalServiceAccountManager       accountManager   = AccountManagerFactory.getInstance().createAuthenticated(context, aci, pni, registrationData.getE164(), SignalServiceAddress.DEFAULT_DEVICE_ID, registrationData.getPassword());
    SignalServiceAccountDataStoreImpl aciProtocolStore = ApplicationDependencies.getProtocolStore().aci();
    SignalServiceAccountDataStoreImpl pniProtocolStore = ApplicationDependencies.getProtocolStore().pni();

    generateAndRegisterPreKeys(ServiceIdType.ACI, accountManager, aciProtocolStore, SignalStore.account().aciPreKeys());
    generateAndRegisterPreKeys(ServiceIdType.PNI, accountManager, pniProtocolStore, SignalStore.account().pniPreKeys());

    if (registrationData.isFcm()) {
      accountManager.setGcmId(Optional.ofNullable(registrationData.getFcmToken()));
    }

    RecipientTable recipientTable = SignalDatabase.recipients();
    RecipientId    selfId         = Recipient.trustedPush(aci, pni, registrationData.getE164()).getId();

    recipientTable.setProfileSharing(selfId, true);
    recipientTable.markRegisteredOrThrow(selfId, aci);
    recipientTable.linkIdsForSelf(aci, pni, registrationData.getE164());
    recipientTable.setProfileKey(selfId, registrationData.getProfileKey());

    ApplicationDependencies.getRecipientCache().clearSelf();

    SignalStore.account().setE164(registrationData.getE164());
    SignalStore.account().setFcmToken(registrationData.getFcmToken());
    SignalStore.account().setFcmEnabled(registrationData.isFcm());

    long now = System.currentTimeMillis();
    saveOwnIdentityKey(selfId, aciProtocolStore, now);
    saveOwnIdentityKey(selfId, pniProtocolStore, now);

    SignalStore.account().setServicePassword(registrationData.getPassword());
    SignalStore.account().setRegistered(true);
    TextSecurePreferences.setPromptedPushRegistration(context, true);
    TextSecurePreferences.setUnauthorizedReceived(context, false);
    NotificationManagerCompat.from(context).cancel(NotificationIds.UNREGISTERED_NOTIFICATION_ID);

    PinState.onRegistration(context, kbsData, pin, hasPin, setRegistrationLockEnabled);

    ApplicationDependencies.closeConnections();
    ApplicationDependencies.getIncomingMessageObserver();
  }

  private void generateAndRegisterPreKeys(@NonNull ServiceIdType serviceIdType,
                                          @NonNull SignalServiceAccountManager accountManager,
                                          @NonNull SignalServiceAccountDataStore protocolStore,
                                          @NonNull PreKeyMetadataStore metadataStore)
      throws IOException
  {
    SignedPreKeyRecord      signedPreKey          = PreKeyUtil.generateAndStoreSignedPreKey(protocolStore, metadataStore);
    List<PreKeyRecord>      oneTimeEcPreKeys      = PreKeyUtil.generateAndStoreOneTimeEcPreKeys(protocolStore, metadataStore);
    KyberPreKeyRecord       lastResortKyberPreKey = PreKeyUtil.generateAndStoreLastResortKyberPreKey(protocolStore, metadataStore);
    List<KyberPreKeyRecord> oneTimeKyberPreKeys   = PreKeyUtil.generateAndStoreOneTimeKyberPreKeys(protocolStore, metadataStore);

    accountManager.setPreKeys(new PreKeyUpload(serviceIdType,
                                               protocolStore.getIdentityKeyPair().getPublicKey(),
                                               signedPreKey,
                                               oneTimeEcPreKeys,
                                               lastResortKyberPreKey,
                                               oneTimeKyberPreKeys));
    metadataStore.setActiveSignedPreKeyId(signedPreKey.getId());
    metadataStore.setSignedPreKeyRegistered(true);
  }

  private void saveOwnIdentityKey(@NonNull RecipientId selfId, @NonNull SignalServiceAccountDataStoreImpl protocolStore, long now) {
    protocolStore.identities().saveIdentityWithoutSideEffects(selfId,
                                                              protocolStore.getIdentityKeyPair().getPublicKey(),
                                                              IdentityTable.VerifiedStatus.VERIFIED,
                                                              true,
                                                              now,
                                                              true);
  }

  @WorkerThread
  private static @Nullable ProfileKey findExistingProfileKey(@NonNull String e164number) {
    RecipientTable        recipientTable = SignalDatabase.recipients();
    Optional<RecipientId> recipient      = recipientTable.getByE164(e164number);

    if (recipient.isPresent()) {
      return ProfileKeyUtil.profileKeyOrNull(Recipient.resolved(recipient.get()).getProfileKey());
    }

    return null;
  }

  public Single<BackupAuthCheckProcessor> getKbsAuthCredential(@NonNull RegistrationData registrationData, List<String> usernamePasswords) {
    SignalServiceAccountManager accountManager = AccountManagerFactory.getInstance().createUnauthenticated(context, registrationData.getE164(), SignalServiceAddress.DEFAULT_DEVICE_ID, registrationData.getPassword());

    return accountManager.checkBackupAuthCredentials(registrationData.getE164(), usernamePasswords)
                         .map(BackupAuthCheckProcessor::new)
                         .doOnSuccess(processor -> {
                           if (SignalStore.kbsValues().removeAuthTokens(processor.getInvalid())) {
                             new BackupManager(context).dataChanged();
                           }
                         });
  }
}
