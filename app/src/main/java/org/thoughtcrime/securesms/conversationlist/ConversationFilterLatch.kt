package org.mycrimes.insecuretests.conversationlist

/**
 * Small state machine that describes moving and triggering actions
 * based off pulling down the conversation filter.
 */
enum class ConversationFilterLatch {
  SET,
  RESET;
}
