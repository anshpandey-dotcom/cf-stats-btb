package com.lagradost.cloudstream3.ui

import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager

object PopupBlocker {
    private val blockedWords = listOf("donation", "donate", "supportdialog", "kofi")

    private fun isNag(fragment: Fragment): Boolean {
        if (fragment !is DialogFragment) return false
        val name = fragment.javaClass.name.lowercase()
        val tag = fragment.tag?.lowercase().orEmpty()
        return blockedWords.any { name.contains(it) || tag.contains(it) }
    }

    fun attach(activity: FragmentActivity) {
        activity.supportFragmentManager.registerFragmentLifecycleCallbacks(
            object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentStarted(fm: FragmentManager, f: Fragment) {
                    if (isNag(f)) {
                        (f as DialogFragment).dialog?.hide()
                        runCatching { f.dismissAllowingStateLoss() }
                    }
                }
            },
            true
        )
    }
}
