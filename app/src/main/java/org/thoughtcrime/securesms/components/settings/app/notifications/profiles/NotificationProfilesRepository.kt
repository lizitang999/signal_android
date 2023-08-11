package org.mycrimes.insecuretests.components.settings.app.notifications.profiles

import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.ObservableEmitter
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.mycrimes.insecuretests.conversation.colors.AvatarColor
import org.mycrimes.insecuretests.database.DatabaseObserver
import org.mycrimes.insecuretests.database.NotificationProfileDatabase
import org.mycrimes.insecuretests.database.RxDatabaseObserver
import org.mycrimes.insecuretests.database.SignalDatabase
import org.mycrimes.insecuretests.dependencies.ApplicationDependencies
import org.mycrimes.insecuretests.keyvalue.SignalStore
import org.mycrimes.insecuretests.notifications.profiles.NotificationProfile
import org.mycrimes.insecuretests.notifications.profiles.NotificationProfileSchedule
import org.mycrimes.insecuretests.notifications.profiles.NotificationProfiles
import org.mycrimes.insecuretests.recipients.RecipientId
import org.mycrimes.insecuretests.util.toLocalDateTime
import org.mycrimes.insecuretests.util.toMillis

/**
 * One stop shop for all your Notification Profile data needs.
 */
class NotificationProfilesRepository {
  private val database: NotificationProfileDatabase = SignalDatabase.notificationProfiles

  fun getProfiles(): Flowable<List<NotificationProfile>> {
    return RxDatabaseObserver
      .notificationProfiles
      .map { database.getProfiles() }
      .subscribeOn(Schedulers.io())
  }

  fun getProfile(profileId: Long): Observable<NotificationProfile> {
    return Observable.create { emitter: ObservableEmitter<NotificationProfile> ->
      val emitProfile: () -> Unit = {
        val profile: NotificationProfile? = database.getProfile(profileId)
        if (profile != null) {
          emitter.onNext(profile)
        } else {
          emitter.onError(NotificationProfileNotFoundException())
        }
      }

      val databaseObserver: DatabaseObserver = ApplicationDependencies.getDatabaseObserver()
      val profileObserver = DatabaseObserver.Observer { emitProfile() }

      databaseObserver.registerNotificationProfileObserver(profileObserver)

      emitter.setCancellable { databaseObserver.unregisterObserver(profileObserver) }
      emitProfile()
    }.subscribeOn(Schedulers.io())
  }

  fun createProfile(name: String, selectedEmoji: String): Single<NotificationProfileDatabase.NotificationProfileChangeResult> {
    return Single.fromCallable { database.createProfile(name = name, emoji = selectedEmoji, color = AvatarColor.random(), createdAt = System.currentTimeMillis()) }
      .subscribeOn(Schedulers.io())
  }

  fun updateProfile(profileId: Long, name: String, selectedEmoji: String): Single<NotificationProfileDatabase.NotificationProfileChangeResult> {
    return Single.fromCallable { database.updateProfile(profileId = profileId, name = name, emoji = selectedEmoji) }
      .subscribeOn(Schedulers.io())
  }

  fun updateProfile(profile: NotificationProfile): Single<NotificationProfileDatabase.NotificationProfileChangeResult> {
    return Single.fromCallable { database.updateProfile(profile) }
      .subscribeOn(Schedulers.io())
  }

  fun updateAllowedMembers(profileId: Long, recipients: Set<RecipientId>): Single<NotificationProfile> {
    return Single.fromCallable { database.setAllowedRecipients(profileId, recipients) }
      .subscribeOn(Schedulers.io())
  }

  fun removeMember(profileId: Long, recipientId: RecipientId): Single<NotificationProfile> {
    return Single.fromCallable { database.removeAllowedRecipient(profileId, recipientId) }
      .subscribeOn(Schedulers.io())
  }

  fun addMember(profileId: Long, recipientId: RecipientId): Single<NotificationProfile> {
    return Single.fromCallable { database.addAllowedRecipient(profileId, recipientId) }
      .subscribeOn(Schedulers.io())
  }

  fun deleteProfile(profileId: Long): Completable {
    return Completable.fromCallable { database.deleteProfile(profileId) }
      .subscribeOn(Schedulers.io())
  }

  fun updateSchedule(schedule: NotificationProfileSchedule): Completable {
    return Completable.fromCallable { database.updateSchedule(schedule) }
      .subscribeOn(Schedulers.io())
  }

  fun manuallyToggleProfile(profile: NotificationProfile, now: Long = System.currentTimeMillis()): Completable {
    return manuallyToggleProfile(profile.id, profile.schedule, now)
  }

  fun manuallyToggleProfile(profileId: Long, schedule: NotificationProfileSchedule, now: Long = System.currentTimeMillis()): Completable {
    return Completable.fromAction {
      val profiles = database.getProfiles()
      val activeProfile = NotificationProfiles.getActiveProfile(profiles, now)

      if (profileId == activeProfile?.id) {
        SignalStore.notificationProfileValues().manuallyEnabledProfile = 0
        SignalStore.notificationProfileValues().manuallyEnabledUntil = 0
        SignalStore.notificationProfileValues().manuallyDisabledAt = now
        SignalStore.notificationProfileValues().lastProfilePopup = 0
        SignalStore.notificationProfileValues().lastProfilePopupTime = 0
      } else {
        val inScheduledWindow = schedule.isCurrentlyActive(now)
        SignalStore.notificationProfileValues().manuallyEnabledProfile = profileId
        SignalStore.notificationProfileValues().manuallyEnabledUntil = if (inScheduledWindow) schedule.endDateTime(now.toLocalDateTime()).toMillis() else Long.MAX_VALUE
        SignalStore.notificationProfileValues().manuallyDisabledAt = now
      }
    }
      .doOnComplete { ApplicationDependencies.getDatabaseObserver().notifyNotificationProfileObservers() }
      .subscribeOn(Schedulers.io())
  }

  fun manuallyEnableProfileForDuration(profileId: Long, enableUntil: Long, now: Long = System.currentTimeMillis()): Completable {
    return Completable.fromAction {
      SignalStore.notificationProfileValues().manuallyEnabledProfile = profileId
      SignalStore.notificationProfileValues().manuallyEnabledUntil = enableUntil
      SignalStore.notificationProfileValues().manuallyDisabledAt = now
    }
      .doOnComplete { ApplicationDependencies.getDatabaseObserver().notifyNotificationProfileObservers() }
      .subscribeOn(Schedulers.io())
  }

  fun manuallyEnableProfileForSchedule(profileId: Long, schedule: NotificationProfileSchedule, now: Long = System.currentTimeMillis()): Completable {
    return Completable.fromAction {
      val inScheduledWindow = schedule.isCurrentlyActive(now)
      SignalStore.notificationProfileValues().manuallyEnabledProfile = if (inScheduledWindow) profileId else 0
      SignalStore.notificationProfileValues().manuallyEnabledUntil = if (inScheduledWindow) schedule.endDateTime(now.toLocalDateTime()).toMillis() else Long.MAX_VALUE
      SignalStore.notificationProfileValues().manuallyDisabledAt = if (inScheduledWindow) now else 0
    }
      .doOnComplete { ApplicationDependencies.getDatabaseObserver().notifyNotificationProfileObservers() }
      .subscribeOn(Schedulers.io())
  }

  class NotificationProfileNotFoundException : Throwable()
}
