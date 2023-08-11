package org.mycrimes.insecuretests.database.model

import android.net.Uri
import org.signal.libsignal.zkgroup.groups.GroupMasterKey
import org.signal.libsignal.zkgroup.profiles.ExpiringProfileKeyCredential
import org.mycrimes.insecuretests.badges.models.Badge
import org.mycrimes.insecuretests.conversation.colors.AvatarColor
import org.mycrimes.insecuretests.conversation.colors.ChatColors
import org.mycrimes.insecuretests.database.IdentityTable.VerifiedStatus
import org.mycrimes.insecuretests.database.RecipientTable
import org.mycrimes.insecuretests.database.RecipientTable.InsightsBannerTier
import org.mycrimes.insecuretests.database.RecipientTable.MentionSetting
import org.mycrimes.insecuretests.database.RecipientTable.RegisteredState
import org.mycrimes.insecuretests.database.RecipientTable.UnidentifiedAccessMode
import org.mycrimes.insecuretests.database.RecipientTable.VibrateState
import org.mycrimes.insecuretests.groups.GroupId
import org.mycrimes.insecuretests.profiles.ProfileName
import org.mycrimes.insecuretests.recipients.Recipient
import org.mycrimes.insecuretests.recipients.RecipientId
import org.mycrimes.insecuretests.service.webrtc.links.CallLinkRoomId
import org.mycrimes.insecuretests.wallpaper.ChatWallpaper
import org.whispersystems.signalservice.api.push.PNI
import org.whispersystems.signalservice.api.push.ServiceId
import java.util.Optional

/**
 * Database model for [RecipientTable].
 */
data class RecipientRecord(
  val id: RecipientId,
  val serviceId: ServiceId?,
  val pni: PNI?,
  val username: String?,
  val e164: String?,
  val email: String?,
  val groupId: GroupId?,
  val distributionListId: DistributionListId?,
  val groupType: RecipientTable.GroupType,
  val isBlocked: Boolean,
  val muteUntil: Long,
  val messageVibrateState: VibrateState,
  val callVibrateState: VibrateState,
  val messageRingtone: Uri?,
  val callRingtone: Uri?,
  private val defaultSubscriptionId: Int,
  val expireMessages: Int,
  val registered: RegisteredState,
  val profileKey: ByteArray?,
  val expiringProfileKeyCredential: ExpiringProfileKeyCredential?,
  val systemProfileName: ProfileName,
  val systemDisplayName: String?,
  val systemContactPhotoUri: String?,
  val systemPhoneLabel: String?,
  val systemContactUri: String?,
  @get:JvmName("getProfileName")
  val signalProfileName: ProfileName,
  @get:JvmName("getProfileAvatar")
  val signalProfileAvatar: String?,
  val profileAvatarFileDetails: ProfileAvatarFileDetails,
  @get:JvmName("isProfileSharing")
  val profileSharing: Boolean,
  val lastProfileFetch: Long,
  val notificationChannel: String?,
  val unidentifiedAccessMode: UnidentifiedAccessMode,
  @get:JvmName("isForceSmsSelection")
  val forceSmsSelection: Boolean,
  val capabilities: Capabilities,
  val insightsBannerTier: InsightsBannerTier,
  val storageId: ByteArray?,
  val mentionSetting: MentionSetting,
  val wallpaper: ChatWallpaper?,
  val chatColors: ChatColors?,
  val avatarColor: AvatarColor,
  val about: String?,
  val aboutEmoji: String?,
  val syncExtras: SyncExtras,
  val extras: Recipient.Extras?,
  @get:JvmName("hasGroupsInCommon")
  val hasGroupsInCommon: Boolean,
  val badges: List<Badge>,
  @get:JvmName("needsPniSignature")
  val needsPniSignature: Boolean,
  val isHidden: Boolean,
  val callLinkRoomId: CallLinkRoomId?
) {

  fun getDefaultSubscriptionId(): Optional<Int> {
    return if (defaultSubscriptionId != -1) Optional.of(defaultSubscriptionId) else Optional.empty()
  }

  fun e164Only(): Boolean {
    return this.e164 != null && this.serviceId == null
  }

  fun sidOnly(sid: ServiceId): Boolean {
    return this.e164 == null && this.serviceId == sid && (this.pni == null || this.pni == sid)
  }

  fun sidIsPni(): Boolean {
    return this.serviceId != null && this.pni != null && this.serviceId == this.pni
  }

  fun pniAndAci(): Boolean {
    return this.serviceId != null && this.pni != null && this.serviceId != this.pni
  }

  /**
   * A bundle of data that's only necessary when syncing to storage service, not for a
   * [Recipient].
   */
  data class SyncExtras(
    val storageProto: ByteArray?,
    val groupMasterKey: GroupMasterKey?,
    val identityKey: ByteArray?,
    val identityStatus: VerifiedStatus,
    val isArchived: Boolean,
    val isForcedUnread: Boolean,
    val unregisteredTimestamp: Long,
    val systemNickname: String?
  )

  data class Capabilities(
    val rawBits: Long,
    val groupsV1MigrationCapability: Recipient.Capability,
    val senderKeyCapability: Recipient.Capability,
    val announcementGroupCapability: Recipient.Capability,
    val changeNumberCapability: Recipient.Capability,
    val storiesCapability: Recipient.Capability,
    val giftBadgesCapability: Recipient.Capability,
    val pnpCapability: Recipient.Capability,
    val paymentActivation: Recipient.Capability
  ) {
    companion object {
      @JvmField
      val UNKNOWN = Capabilities(
        0,
        Recipient.Capability.UNKNOWN,
        Recipient.Capability.UNKNOWN,
        Recipient.Capability.UNKNOWN,
        Recipient.Capability.UNKNOWN,
        Recipient.Capability.UNKNOWN,
        Recipient.Capability.UNKNOWN,
        Recipient.Capability.UNKNOWN,
        Recipient.Capability.UNKNOWN
      )
    }
  }
}
