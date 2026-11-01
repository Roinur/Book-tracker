package com.roinur.booktracker

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentDialog
import androidx.compose.material3.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** In-memory dialog fixture in its own window; never changes the daily library or active session. */
class BookCompletionRatingVisualTest {
    @Test fun unratedDialogRequiresAStarSavesAndCanBeSkipped() {
        val inst=InstrumentationRegistry.getInstrumentation();val context=inst.targetContext
        val activity=inst.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val book=BookRow(-42,"","TESTDATA – Finished book","Test author",200,200,BookStatus.FINISHED,0,"","","","",false,"","","","",3600)
        val visible=mutableStateOf(true);var saved=0;var skipped=0
        var host: ComponentDialog?=null;var view: ComposeView?=null
        fun descend(n: AccessibilityNodeInfo): List<AccessibilityNodeInfo> = listOf(n)+(0 until n.childCount).flatMap {n.getChild(it)?.let(::descend).orEmpty()}
        fun nodes()=inst.uiAutomation.rootInActiveWindow?.let(::descend).orEmpty().filter {it.packageName?.toString()==context.packageName}
        fun find(label: String): AccessibilityNodeInfo {
            repeat(80) {nodes().firstOrNull {it.text?.toString()==label}?.let {return it};SystemClock.sleep(100)}
            error("Missing fixture control $label")
        }
        fun clickable(n: AccessibilityNodeInfo): AccessibilityNodeInfo {var target=n;while(!target.isClickable&&target.parent!=null)target=target.parent;return target}
        fun click(label: String) {assertTrue(clickable(find(label)).performAction(AccessibilityNodeInfo.ACTION_CLICK));inst.waitForIdleSync();SystemClock.sleep(150)}
        fun awaitEnabled(label: String) {
            repeat(80) {
                nodes().firstOrNull {it.text?.toString()==label}?.let {if(clickable(it).isEnabled)return}
                SystemClock.sleep(100)
            }
            error("Fixture control did not become enabled: $label")
        }
        try {
            inst.runOnMainSync {
                view=ComposeView(activity).apply {id=View.generateViewId()}
                host=ComponentDialog(activity).also {it.setContentView(view!!);it.show();it.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT)}
                view!!.setContent {
                    MaterialTheme(colorScheme=darkColorScheme(primary=Color(0xFFFFB4A9),surfaceContainerLow=Color(0xFF15121F))) {
                        if(visible.value) BookCompletionRatingDialog(book,{skipped++;visible.value=false}) {rating,done->saved=rating;visible.value=false;done(true)}
                    }
                }
            }
            assertFalse(clickable(find("Save rating")).isEnabled)
            val stars=nodes().filter {it.text?.toString()=="☆"};assertEquals(5,stars.size)
            assertTrue(clickable(stars[3]).performAction(AccessibilityNodeInfo.ACTION_CLICK));inst.waitForIdleSync()
            awaitEnabled("Save rating")
            inst.uiAutomation.takeScreenshot()?.let {shot->File(context.externalCacheDir,"completion-rating-qa.png").outputStream().use {shot.compress(Bitmap.CompressFormat.PNG,100,it)};shot.recycle()}
            click("Save rating");assertEquals(4,saved)
            inst.runOnMainSync {visible.value=true};click("Not now");assertEquals(1,skipped);assertEquals(4,saved)
        } finally {inst.runOnMainSync {view?.disposeComposition();host?.dismiss()}}
    }
}
