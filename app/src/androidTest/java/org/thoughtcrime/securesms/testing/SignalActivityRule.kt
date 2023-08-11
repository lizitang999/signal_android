package org.mycrimes.insecuretests.testing

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.mockwebserver.MockResponse
import org.junit.rules.ExternalResource
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.mycrimes.insecuretests.SignalInstrumentationApplicationContext
import org.mycrimes.insecuretests.crypto.IdentityKeyUtil
import org.mycrimes.insecuretests.crypto.MasterSecretUtil
import org.mycrimes.insecuretests.crypto.ProfileKeyUtil
import org.mycrimes.insecuretests.database.IdentityTable
import org.mycrimes.insecuretests.database.SignalDatabase
import org.mycrimes.insecuretests.dependencies.ApplicationDependencies
import org.mycrimes.insecuretests.dependencies.InstrumentationApplicationDependencyProvider
import org.mycrimes.insecuretests.keyvalue.SignalStore
import org.mycrimes.insecuretests.profiles.ProfileName
import org.mycrimes.insecuretests.recipients.Recipient
import org.mycrimes.insecuretests.recipients.RecipientId
import org.mycrimes.insecuretests.registration.RegistrationData
import org.mycrimes.insecuretests.registration.RegistrationRepository
import org.mycrimes.insecuretests.registration.RegistrationUtil
import org.mycrimes.insecuretests.registration.VerifyResponse
import org.mycrimes.insecuretests.util.Util
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile
import org.whispersystems.signalservice.api.push.ACI
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import org.whispersystems.signalservice.internal.ServiceResponse
import org.whispersystems.signalservice.internal.ServiceResponseProcessor
import org.whispersystems.signalservice.internal.push.VerifyAccountResponse
import java.lang.IllegalArgumentException
import java.util.UUID

/**
 * Test rule to use that sets up the application in a mostly registered state. Enough so that most
 * activities should be launchable directly.
 *
 * To use: `@get:Rule val harness = SignalActivityRule()`
 */
class SignalActivityRule(private val othersCount: Int = 4) : ExternalResource() {

  val application: Application = ApplicationDependencies.getApplication()

  lateinit var context: Context
    private set
  lateinit var self: Recipient
    private set
  lateinit var others: List<RecipientId>
    private set
  lateinit var othersKeys: List<IdentityKeyPair>

  val inMemoryLogger: InMemoryLogger
    get() = (application as SignalInstrumentationApplicationContext).inMemoryLogger

  override fun before() {
    context = InstrumentationRegistry.getInstrumentation().targetContext
    self = setupSelf()

    val setupOthers = setupOthers()
    others = setupOthers.first
    othersKeys = setupOthers.second

    InstrumentationApplicationDependencyProvider.clearHandlers()
  }

  private fun setupSelf(): Recipient {
    PreferenceManager.getDefaultSharedPreferences(application).edit().putBoolean("pref_prompted_push_registration", true).commit()
    val masterSecret = MasterSecretUtil.generateMasterSecret(application, MasterSecretUtil.UNENCRYPTED_PASSPHRASE)
    MasterSecretUtil.generateAsymmetricMasterSecret(application, masterSecret)
    val preferences: SharedPreferences = application.getSharedPreferences(MasterSecretUtil.PREFERENCES_NAME, 0)
    preferences.edit().putBoolean("passphrase_initialized", true).commit()

    val registrationRepository = RegistrationRepository(application)

    InstrumentationApplicationDependencyProvider.addMockWebRequestHandlers(Put("/v2/keys") { MockResponse().success() })
    val response: ServiceResponse<VerifyResponse> = registrationRepository.registerAccount(
      RegistrationData(
        code = "123123",
        e164 = "+15555550101",
        password = Util.getSecret(18),
        registrationId = registrationRepository.registrationId,
        profileKey = registrationRepository.getProfileKey("+15555550101"),
        fcmToken = null,
        pniRegistrationId = registrationRepository.pniRegistrationId,
        recoveryPassword = "asdfasdfasdfasdf"
      ),
      VerifyResponse(VerifyAccountResponse(UUID.randomUUID().toString(), UUID.randomUUID().toString(), false), null, null),
      false
    ).blockingGet()

    ServiceResponseProcessor.DefaultProcessor(response).resultOrThrow

    SignalStore.kbsValues().optOut()
    RegistrationUtil.maybeMarkRegistrationComplete()
    SignalDatabase.recipients.setProfileName(Recipient.self().id, ProfileName.fromParts("Tester", "McTesterson"))

    SignalStore.settings().isMessageNotificationsEnabled = false

    return Recipient.self()
  }

  private fun setupOthers(): Pair<List<RecipientId>, List<IdentityKeyPair>> {
    val others = mutableListOf<RecipientId>()
    val othersKeys = mutableListOf<IdentityKeyPair>()

    if (othersCount !in 0 until 1000) {
      throw IllegalArgumentException("$othersCount must be between 0 and 1000")
    }

    for (i in 0 until othersCount) {
      val aci = ACI.from(UUID.randomUUID())
      val recipientId = RecipientId.from(SignalServiceAddress(aci, "+15555551%03d".format(i)))
      SignalDatabase.recipients.setProfileName(recipientId, ProfileName.fromParts("Buddy", "#$i"))
      SignalDatabase.recipients.setProfileKeyIfAbsent(recipientId, ProfileKeyUtil.createNew())
      SignalDatabase.recipients.setCapabilities(recipientId, SignalServiceProfile.Capabilities(true, true, true, true, true, true, true, true, true))
      SignalDatabase.recipients.setProfileSharing(recipientId, true)
      SignalDatabase.recipients.markRegistered(recipientId, aci)
      val otherIdentity = IdentityKeyUtil.generateIdentityKeyPair()
      ApplicationDependencies.getProtocolStore().aci().saveIdentity(SignalProtocolAddress(aci.toString(), 0), otherIdentity.publicKey)
      others += recipientId
      othersKeys += otherIdentity
    }

    return others to othersKeys
  }

  inline fun <reified T : Activity> launchActivity(initIntent: Intent.() -> Unit = {}): ActivityScenario<T> {
    return androidx.test.core.app.launchActivity(Intent(context, T::class.java).apply(initIntent))
  }

  fun changeIdentityKey(recipient: Recipient, identityKey: IdentityKey = IdentityKeyUtil.generateIdentityKeyPair().publicKey) {
    ApplicationDependencies.getProtocolStore().aci().saveIdentity(SignalProtocolAddress(recipient.requireServiceId().toString(), 0), identityKey)
  }

  fun getIdentity(recipient: Recipient): IdentityKey {
    return ApplicationDependencies.getProtocolStore().aci().identities().getIdentity(SignalProtocolAddress(recipient.requireServiceId().toString(), 0))
  }

  fun setVerified(recipient: Recipient, status: IdentityTable.VerifiedStatus) {
    ApplicationDependencies.getProtocolStore().aci().identities().setVerified(recipient.id, getIdentity(recipient), IdentityTable.VerifiedStatus.VERIFIED)
  }
}
