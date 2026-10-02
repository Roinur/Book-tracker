package com.roinur.booktracker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import com.roinur.booktracker.BookFitText as Text

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
internal fun BookGoalCard(book: BookRow, goal: BookGoal?, progress: BookGoalProgress?, onConfigure: () -> Unit,
                          modifier: Modifier = Modifier, openRequest: Int = 0) {
    val pager = rememberPagerState(initialPage = if (goal != null && progress?.completed == false) 1 else 0, pageCount = { 2 })
    LaunchedEffect(book.id, goal != null) { pager.scrollToPage(if (goal != null && progress?.completed == false) 1 else 0) }
    LaunchedEffect(book.id, openRequest) { if (openRequest > 0) pager.scrollToPage(1) }
    BookPanel(modifier.height(120.dp), contentPadding = PaddingValues(9.dp), contentSpacing = 0.dp) {
        HorizontalPager(pager, pageSpacing = 16.dp, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
            if (page == 0) Column(Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)).padding(horizontal = 2.dp), verticalArrangement = Arrangement.SpaceEvenly) {
                BookMetricLabel(R.drawable.ic_book_24, "Pages read")
                Text(if (book.pageCount > 0) "${book.currentPage} / ${book.pageCount}" else "${book.currentPage}",
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                LinearProgressIndicator(progress = { book.progressFraction }, strokeCap = StrokeCap.Round,
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape))
            } else Column(Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)).padding(horizontal = 2.dp).fittedClickable(RoundedCornerShape(6.dp), onClick = onConfigure),
                verticalArrangement = Arrangement.SpaceEvenly) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(painterResource(R.drawable.ic_book_goal_24), null, Modifier.size(16.dp), MaterialTheme.colorScheme.primary)
                    Text(if (goal == null) "Book goal" else "Today's goal", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                    Icon(painterResource(R.drawable.ic_edit_24), "Configure book goal", Modifier.size(14.dp), MaterialTheme.colorScheme.primary)
                }
                if (progress == null) {
                    Text("Add goal", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold)
                    Text("Finish by · Pages daily", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(if (progress.completed) "Book completed" else "${progress.todayRemaining} pages left",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(if (progress.completed || progress.todayRemaining == 0L) "Today's goal reached" else
                        progress.todaySeconds?.let { "~${bookFormatCompactHours(it)} today" } ?: "Time not available yet",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    LinearProgressIndicator(progress = { progress.todayFraction }, strokeCap = StrokeCap.Round,
                        modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape))
                    Text("${progress.remainingPages}p · ${progress.daysRemaining}d" + progress.remainingSeconds?.let { " · ~${bookFormatCompactHours(it)}" }.orEmpty(),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            repeat(2) { page ->
                Box(Modifier.size(22.dp, 12.dp)
                    .semantics { contentDescription = if (page == 0) "Pages read page" else "Book goal page" }, contentAlignment = Alignment.Center) {
                    Box(Modifier.size(4.dp).background(if (pager.currentPage == page) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant, CircleShape))
                }
            }
        }
    }
}
