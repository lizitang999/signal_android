package org.mycrimes.insecuretests.conversation

import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import org.signal.core.util.dp
import org.mycrimes.insecuretests.R
import org.mycrimes.insecuretests.components.menu.ActionItem
import org.mycrimes.insecuretests.components.menu.SignalContextMenu
import org.mycrimes.insecuretests.util.DateUtils
import org.mycrimes.insecuretests.util.toLocalDateTime
import org.mycrimes.insecuretests.util.toMillis
import java.util.Locale

class ScheduleMessageContextMenu {

  companion object {

    private val presetHours = arrayOf(8, 12, 18, 21)

    @JvmStatic
    fun show(anchor: View, container: ViewGroup, action: (Long) -> Unit): SignalContextMenu {
      val currentTime = System.currentTimeMillis()
      val scheduledTimes = getNextScheduleTimes(currentTime)
      val actionItems = scheduledTimes.map {
        if (it > 0) {
          ActionItem(getIconForTime(it), DateUtils.getScheduledMessageDateString(anchor.context, Locale.getDefault(), it)) {
            action(it)
          }
        } else {
          ActionItem(R.drawable.symbol_calendar_24, anchor.context.getString(R.string.ScheduledMessages_pick_time)) {
            action(it)
          }
        }
      }

      return SignalContextMenu.Builder(anchor, container)
        .offsetX(12.dp)
        .offsetY(12.dp)
        .preferredVerticalPosition(SignalContextMenu.VerticalPosition.ABOVE)
        .show(actionItems)
    }

    @DrawableRes
    private fun getIconForTime(timeMs: Long): Int {
      val dateTime = timeMs.toLocalDateTime()
      return if (dateTime.hour >= 18) {
        R.drawable.ic_nighttime_26
      } else {
        R.drawable.ic_daytime_24
      }
    }

    private fun getNextScheduleTimes(currentTimeMs: Long): List<Long> {
      var currentDateTime = currentTimeMs.toLocalDateTime()

      val timestampList = ArrayList<Long>(4)
      var presetIndex = presetHours.indexOfFirst { it > currentDateTime.hour }
      if (presetIndex == -1) {
        currentDateTime = currentDateTime.plusDays(1)
        presetIndex = 0
      }
      currentDateTime = currentDateTime.withMinute(0).withSecond(0)
      while (timestampList.size < 3) {
        currentDateTime = currentDateTime.withHour(presetHours[presetIndex])
        timestampList += currentDateTime.toMillis()
        presetIndex++
        if (presetIndex >= presetHours.size) {
          presetIndex = 0
          currentDateTime = currentDateTime.plusDays(1)
        }
      }
      timestampList += -1

      return timestampList.reversed()
    }
  }
}
