package com.wareceptionist.app

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class WhatsAppAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val prefs = getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)

        val lastBotReplyTime = prefs.getLong("last_bot_reply_time", 0L)
        // Window of 5 seconds to allow WhatsApp to open and the button to appear
        if (System.currentTimeMillis() - lastBotReplyTime > 5000) {
            return 
        }

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || 
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            
            val rootNode = rootInActiveWindow ?: return

            // Search by common Send button IDs
            val sendNodes = rootNode.findAccessibilityNodeInfosByViewId("com.whatsapp:id/send")
            val w4bNodes = rootNode.findAccessibilityNodeInfosByViewId("com.whatsapp.w4b:id/send")
            
            val allSendNodes = mutableListOf<AccessibilityNodeInfo>()
            if (sendNodes != null) allSendNodes.addAll(sendNodes)
            if (w4bNodes != null) allSendNodes.addAll(w4bNodes)
            
            if (allSendNodes.isNotEmpty()) {
                for (node in allSendNodes) {
                    if (node.isClickable) {
                        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        AppLogger.log(this, "🤖 Phase 2: Sent message via Accessibility!")
                        prefs.edit().putLong("last_bot_reply_time", 0L).apply()
                        // Relaunch our app to hide WhatsApp immediately
                        val launchIntent = android.content.Intent(this, MainActivity::class.java).apply {
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                        startActivity(launchIntent)
                        
                        return
                    }
                }
            } else {
                // Fallback: search by content description "Send"
                val allNodes = mutableListOf<AccessibilityNodeInfo>()
                findAllNodes(rootNode, allNodes)
                for (node in allNodes) {
                    val desc = node.contentDescription?.toString() ?: ""
                    if (node.isClickable && desc.equals("Send", ignoreCase = true)) {
                        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        AppLogger.log(this, "🤖 Phase 2: Sent message via Accessibility (Fallback)!")
                        prefs.edit().putLong("last_bot_reply_time", 0L).apply()
                        // Relaunch our app to hide WhatsApp immediately
                        val launchIntent = android.content.Intent(this, MainActivity::class.java).apply {
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                        startActivity(launchIntent)

                        return
                    }
                }
            }
        }
    }

    private fun findAllNodes(node: AccessibilityNodeInfo, list: MutableList<AccessibilityNodeInfo>) {
        list.add(node)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                findAllNodes(child, list)
            }
        }
    }

    override fun onInterrupt() {
        // Not used
    }
}
