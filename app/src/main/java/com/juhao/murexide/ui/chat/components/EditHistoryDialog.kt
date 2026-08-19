package com.juhao.murexide.ui.chat.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.juhao.murexide.repository.EditHistoryItem
import com.juhao.murexide.repository.MessageRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EditHistoryDialog(
    token: String,
    msgId: String,
    onDismiss: () -> Unit
) {
    val repository = MessageRepository()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var historyList by remember { mutableStateOf<List<EditHistoryItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(true) }
    var currentPage by remember { mutableIntStateOf(1) }

    fun getEditHistory(
        loadMore: Boolean = false
    ) {
        scope.launch {
            if (!hasMore) return@launch
            isLoading = true
            repository.getMessageEditHistory(
                token = token,
                page = currentPage,
                msgId = msgId
            ).onSuccess { list ->
                isLoading = false
                if (list.isEmpty()) {
                    hasMore = false
                    return@onSuccess
                }
                historyList = if (loadMore) {
                    historyList + list
                } else {
                    list
                }
            }.onFailure {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        getEditHistory()
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisibleItem to layoutInfo.totalItemsCount
        }.collect { (lastVisibleItem, totalItems) ->
            if (
                totalItems > 0 &&
                lastVisibleItem >= totalItems - 3
            ) {
                currentPage++
                getEditHistory(true)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("编辑历史")
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                state = listState
            ) {
                items(historyList) { item ->
                    Item(item)
                }

                if (isLoading) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            ContainedLoadingIndicator()
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("确定")
            }
        }
    )
}

@Composable
private fun Item(
    data: EditHistoryItem
) {
    val timestampDisplay = remember(data.msgTime) {
        try {
            val date = Date(data.msgTime)
            val now = Date()

            val todayCalendar = Calendar.getInstance().apply {
                time = now
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            when {
                date.after(todayCalendar.time) -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
                date.after(Date(todayCalendar.timeInMillis - 86400000)) -> "昨天 " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
                else -> SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(date)
            }
        } catch (_: Exception) {
            ""
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(data.getOldText())
            Text("编辑时间：$timestampDisplay")
        }
    }
}