package com.roinur.booktracker

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.View
import androidx.activity.ComponentDialog
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.util.UUID

/** Production Stats UI with in-memory fixtures only. No daily database access or demo inserts. */
class BookPeriodRecapVisualTest {
    @Test fun allFourLongPressesAndHistoricalMonthUseTheSelectedDates() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val today=LocalDate.now(); val previous=today.minusMonths(1)
        val previousMonth=bookStatsPeriod(BookStatsRange.MONTH,1,today)
        val fixtureCover=File(context.externalCacheDir,"recap-fixture-${UUID.randomUUID()}.png")
        val cover=Bitmap.createBitmap(160,240,Bitmap.Config.ARGB_8888)
        Canvas(cover).apply {
            drawColor(AndroidColor.rgb(32,55,85)); val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=AndroidColor.WHITE;textSize=22f}
            drawText("TESTDATA",18f,90f,paint); drawText("Åäö",52f,140f,paint)
        }
        fixtureCover.outputStream().use {cover.compress(Bitmap.CompressFormat.PNG,100,it)}; cover.recycle()
        val book=BookRow(-110,"","TESTDATA – En svensk boktitel","Testförfattare",200,111,BookStatus.FINISHED,4,
            "",android.net.Uri.fromFile(fixtureCover).toString(),"","",false,"","",today.toString(),"",6186)
        val sessions=listOf(BookReadingSessionRow(-1,book.id,today.toString(),today.toString(),3600,30,111),
            BookReadingSessionRow(-2,book.id,previous.toString(),previous.toString(),2586,81,81))
        val stats=BookStats(totalBooks=1,finishedBooks=1,totalPages=200,pagesRead=111,readingSeconds=6186,
            dailyActivity=bookDailyActivity(sessions,false),dailyPageActivity=bookDailyActivity(sessions,true))
        val activity=instrumentation.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        var view: ComposeView? = null
        var fixtureDialog: ComponentDialog? = null
        fun descendants(node: AccessibilityNodeInfo): List<AccessibilityNodeInfo> = listOf(node)+(0 until node.childCount).flatMap {node.getChild(it)?.let(::descendants).orEmpty()}
        fun nodes()=instrumentation.uiAutomation.rootInActiveWindow?.let(::descendants).orEmpty()
            .filter {it.packageName?.toString()==context.packageName}
        fun awaitNode(label: String, predicate: (AccessibilityNodeInfo)->Boolean): AccessibilityNodeInfo {
            val deadline=SystemClock.uptimeMillis()+25_000
            while(SystemClock.uptimeMillis()<deadline) {
                nodes().firstOrNull(predicate)?.let {return it}; SystemClock.sleep(150)
            }
            error("Missing $label; visible labels: ${nodes().mapNotNull {it.text?.toString()}.take(30)}")
        }
        fun node(label: String)=awaitNode(label) {it.text?.toString()==label || it.contentDescription?.toString()==label}
        fun click(label: String) {
            var target=node(label); while(!target.isClickable && target.parent!=null) target=target.parent
            assertTrue("Could not click $label",target.performAction(AccessibilityNodeInfo.ACTION_CLICK)); instrumentation.waitForIdleSync(); SystemClock.sleep(200)
        }
        fun hold(label: String) {
            var target=node(label); while(!target.isLongClickable && target.parent!=null) target=target.parent
            assertTrue("Missing long-press semantics for $label",target.isLongClickable)
            val bounds=Rect();target.getBoundsInScreen(bounds)
            val down=SystemClock.uptimeMillis()
            val press=MotionEvent.obtain(down,down,MotionEvent.ACTION_DOWN,bounds.exactCenterX(),bounds.exactCenterY(),0)
            instrumentation.uiAutomation.injectInputEvent(press,true);press.recycle();SystemClock.sleep(900)
            val release=MotionEvent.obtain(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,bounds.exactCenterX(),bounds.exactCenterY(),0)
            instrumentation.uiAutomation.injectInputEvent(release,true);release.recycle();instrumentation.waitForIdleSync()
        }
        fun assertRecap(first: LocalDate, last: LocalDate, pages: Int) {
            awaitNode("rendered recap") {it.contentDescription?.toString()?.let {text->text.contains("$first to $last") && text.contains("$pages pages.")}==true}
            for(button in listOf("Copy image","Save","Share")) { var target=node(button);while(!target.isClickable&&target.parent!=null)target=target.parent;assertTrue(target.isEnabled) }
        }
        try {
            instrumentation.runOnMainSync {
                val fixtureView=ComposeView(activity).apply {id=View.generateViewId()};view=fixtureView
                fixtureDialog=ComponentDialog(activity).also {dialog->
                    dialog.setContentView(fixtureView);dialog.show()
                    dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT)
                }
                fixtureView.setContent {
                    MaterialTheme(colorScheme=darkColorScheme(primary=Color(0xFF8BACFF),background=Color(0xFF0B0A12),surface=Color(0xFF14121E),
                        onSurface=Color(0xFFE5E1EF),onSurfaceVariant=Color(0xFFB9B3CC),surfaceContainerHigh=Color(0xFF211E2B),outlineVariant=Color(0xFF302B40))) {
                        Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
                            BookAdaptiveViewport {
                                BookStatsContent(listOf(book),sessions.map {BookReadingSessionWithBook(it,book.title)},listOf(book),{},stats,
                                    BookReadingGoals(),null,0,0,0,System.currentTimeMillis(),Modifier.safeDrawingPadding().padding(12.dp),
                                    {},{},{},{},{},{true})
                            }
                        }
                    }
                }
            }
            click("Month"); assertFalse(nodes().any {it.text?.toString()=="Reading recap"})
            click("Previous period");hold("Month")
            assertRecap(previousMonth.start,previousMonth.end,81)
            instrumentation.uiAutomation.takeScreenshot()?.let {shot->File(context.externalCacheDir,"period-recap-month-qa.png").outputStream().use {shot.compress(Bitmap.CompressFormat.PNG,100,it)};shot.recycle()}
            click("Close")
            // Long-pressing a different tab switches to its current period; normal taps still only select.
            hold("Week");assertRecap(today.minusDays(6),today,30);click("Close")
            hold("Year");assertRecap(today.minusDays(364),today,111);click("Close")
            hold("All time");assertRecap(previous,today,111);click("Close")

            val colors=BookRecapPalette(0xFF0B0A12.toInt(),0xFF14121E.toInt(),0xFFE5E1EF.toInt(),0xFFB9B3CC.toInt(),
                0xFF8BACFF.toInt(),0xFF252B4A.toInt(),0xFFDDE4FF.toInt(),0xFF302B40.toInt())
            val full=runBlocking {renderBookPeriodRecap(context,buildBookPeriodRecap(listOf(book),sessions,stats,BookStatsRange.MONTH,previousMonth.start,previousMonth.end),colors)}
            try {
                val bitmap=BitmapFactory.decodeFile(full.path);assertNotNull(bitmap);assertEquals(1080,bitmap.width);assertTrue(bitmap.height in 1000..3000);bitmap.recycle()
                val uri=FileProvider.getUriForFile(context,"${context.packageName}.readinglistexports",full)
                context.contentResolver.openInputStream(uri)!!.use {assertEquals(137,it.read());assertEquals(80,it.read())}
                full.copyTo(File(context.externalCacheDir,"period-recap-card-qa.png"),overwrite=true)
            } finally {full.delete()}
            val empty=runBlocking {renderBookPeriodRecap(context,buildBookPeriodRecap(emptyList(),emptyList(),BookStats(),BookStatsRange.WEEK,today.minusDays(6),today),
                colors.copy(background=AndroidColor.WHITE,surface=0xFFF3F5FA.toInt(),ink=AndroidColor.BLACK,muted=AndroidColor.DKGRAY))}
            try {assertTrue(empty.length()>0);empty.copyTo(File(context.externalCacheDir,"period-recap-empty-qa.png"),overwrite=true)} finally {empty.delete()}
        } finally {
            instrumentation.runOnMainSync {view?.disposeComposition();fixtureDialog?.dismiss()}
            fixtureCover.delete()
        }
    }
}
