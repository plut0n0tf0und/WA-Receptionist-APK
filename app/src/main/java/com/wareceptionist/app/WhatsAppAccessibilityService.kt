package com.wareceptionist.app

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class WhatsAppAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val prefs = getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)

        val lastBotReplyTime = prefs.getLong("last_bot_reply_time", 0L)
        // Window of 15 seconds to allow WhatsApp to open and the button to appear
        if (System.currentTimeMillis() - lastBotReplyTime > 15000) {
            return 
        }

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || 
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            
            val rootNode = rootInActiveWindow ?: return

            // Search by common Send button IDs across normal WhatsApp and WhatsApp Business
            val targetIds = arrayOf(
                "com.whatsapp:id/send",
                "com.whatsapp.w4b:id/send",
                "com.whatsapp:id/send_button",
                "com.whatsapp.w4b:id/send_button",
                "com.whatsapp:id/entry_action_button",
                "com.whatsapp.w4b:id/entry_action_button"
            )
            
            val allSendNodes = mutableListOf<AccessibilityNodeInfo>()
            for (id in targetIds) {
                val nodes = rootNode.findAccessibilityNodeInfosByViewId(id)
                if (nodes != null) allSendNodes.addAll(nodes)
            }
            
            for (node in allSendNodes) {
                if (node.isClickable || node.parent?.isClickable == true) {
                    val targetToClick = if (node.isClickable) node else node.parent
                    targetToClick?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    AppLogger.log(this, "🤖 Phase 2: Sent message via Accessibility (ID match)!")
                    prefs.edit().putLong("last_bot_reply_time", 0L).apply()
                    
                    // Relaunch our app to hide WhatsApp immediately
                    val launchIntent = android.content.Intent(this, MainActivity::class.java).apply {
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    startActivity(launchIntent)
                    return
                }
            }

            // Fallback: search node tree by content description "Send"
            val allNodes = mutableListOf<AccessibilityNodeInfo>()
            findAllNodes(rootNode, allNodes)
            for (node in allNodes) {
                val desc = node.contentDescription?.toString() ?: ""
                val resId = node.viewIdResourceName ?: ""
                if ((desc.equals("Send", ignoreCase = true) || resId.contains("send", ignoreCase = true)) && 
                    (node.isClickable || node.parent?.isClickable == true)) {
                    val targetToClick = if (node.isClickable) node else node.parent
                    targetToClick?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    AppLogger.log(this, "🤖 Phase 2: Sent message via Accessibility (Fallback)!")
                    prefs.edit().putLong("last_bot_reply_time", 0L).apply()
                    
                    val launchIntent = android.content.Intent(this, MainActivity::class.java).apply {
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    startActivity(launchIntent)
                    return
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
