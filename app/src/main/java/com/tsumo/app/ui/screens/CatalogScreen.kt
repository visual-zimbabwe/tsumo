package com.tsumo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tsumo.app.TsumoUiState
import com.tsumo.app.data.BookItem
import com.tsumo.app.ui.components.FilterTabItem
import com.tsumo.app.ui.components.HairlineRule
import com.tsumo.app.ui.components.PrimaryTypographicAction
import com.tsumo.app.ui.components.SharpSquareCheckbox
import com.tsumo.app.ui.theme.EBGaramondFamily
import com.tsumo.app.ui.theme.TsumoColors

@Composable
fun CatalogScreen(
    state: TsumoUiState,
    onSelectSection: (Int) -> Unit,
    onLoadHistory: (Int) -> Unit,
    onFullTextQueryChange: (String) -> Unit,
    onTogglePhrase: () -> Unit,
    onSearchFullText: (Int) -> Unit,
    onSelectBook: (BookItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    var ftFocused by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TsumoColors.Surface)
            .padding(horizontal = 24.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterTabItem(
                label = "Download History",
                selected = state.catalogSection == 0,
                onClick = { onSelectSection(0) },
            )
            FilterTabItem(
                label = "Full-Text Search",
                selected = state.catalogSection == 1,
                onClick = { onSelectSection(1) },
            )
        }

        if (state.catalogSection == 0) {
            when {
                state.historyLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 60.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Reading archive ledger…",
                            fontFamily = EBGaramondFamily,
                            fontStyle = FontStyle.Italic,
                            fontSize = 19.sp,
                            color = TsumoColors.Secondary,
                        )
                    }
                }

                state.historyError != null -> {
                    Column(
                        modifier = Modifier.padding(top = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = state.historyError,
                            style = MaterialTheme.typography.bodyMedium.copy(color = TsumoColors.Error),
                        )
                        PrimaryTypographicAction(
                            label = "Retry",
                            onClick = { onLoadHistory(state.historyPage) },
                        )
                    }
                }

                state.historyItems.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 60.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "No download history yet",
                            fontFamily = EBGaramondFamily,
                            fontStyle = FontStyle.Italic,
                            fontSize = 20.sp,
                            color = TsumoColors.Secondary,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        PrimaryTypographicAction(
                            label = "Refresh ledger",
                            onClick = { onLoadHistory(1) },
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 32.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "${state.historyItems.size} ENTRIES · PAGE ${state.historyPage}",
                                    style = MaterialTheme.typography.labelSmall.copy(color = TsumoColors.Outline),
                                )
                                Text(
                                    text = "REFRESH",
                                    style = MaterialTheme.typography.labelSmall.copy(color = TsumoColors.Secondary),
                                    modifier = Modifier
                                        .clickable { onLoadHistory(state.historyPage) }
                                        .padding(vertical = 4.dp),
                                )
                            }
                            HairlineRule()
                        }

                        items(
                            items = state.historyItems,
                            key = { it.commandId.ifBlank { it.url.ifBlank { it.name + it.date } } },
                        ) { item ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectBook(item.toBookItem()) }
                                    .padding(vertical = 15.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = item.name,
                                    fontFamily = EBGaramondFamily,
                                    fontSize = 19.sp,
                                    lineHeight = 24.sp,
                                    color = TsumoColors.Primary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = item.date.ifBlank { "—" }.uppercase(),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = TsumoColors.Secondary,
                                        ),
                                    )
                                    Text(
                                        text = listOf(item.extension.uppercase(), item.size)
                                            .filter { it.isNotBlank() }
                                            .joinToString(" · ")
                                            .ifBlank { "ARCHIVE" },
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = TsumoColors.Secondary.copy(alpha = 0.7f),
                                        ),
                                    )
                                }
                            }
                            HairlineRule()
                        }

                        if (state.historyTotalPages > 1) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 18.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = "PREVIOUS",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = if (state.historyPage > 1) TsumoColors.Primary else TsumoColors.OutlineVariant,
                                        ),
                                        modifier = Modifier
                                            .clickable(enabled = state.historyPage > 1) {
                                                onLoadHistory(state.historyPage - 1)
                                            }
                                            .padding(vertical = 8.dp),
                                    )
                                    Text(
                                        text = "PAGE ${state.historyPage} OF ${state.historyTotalPages}",
                                        style = MaterialTheme.typography.labelSmall.copy(color = TsumoColors.Secondary),
                                    )
                                    Text(
                                        text = "NEXT",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = if (state.historyPage < state.historyTotalPages) TsumoColors.Primary else TsumoColors.OutlineVariant,
                                        ),
                                        modifier = Modifier
                                            .clickable(enabled = state.historyPage < state.historyTotalPages) {
                                                onLoadHistory(state.historyPage + 1)
                                            }
                                            .padding(vertical = 8.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Internet Archive Full-Text Search
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicTextField(
                        value = state.fullTextQuery,
                        onValueChange = onFullTextQueryChange,
                        singleLine = true,
                        cursorBrush = SolidColor(TsumoColors.Primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                focusManager.clearFocus()
                                onSearchFullText(1)
                            }
                        ),
                        textStyle = TextStyle(
                            fontFamily = EBGaramondFamily,
                            fontSize = 20.sp,
                            color = TsumoColors.Primary,
                        ),
                        decorationBox = { innerTextField ->
                            Box(modifier = Modifier.padding(vertical = 8.dp)) {
                                if (state.fullTextQuery.isEmpty()) {
                                    Text(
                                        text = "Search inside book text (Internet Archive)…",
                                        fontFamily = EBGaramondFamily,
                                        fontSize = 20.sp,
                                        color = TsumoColors.Secondary.copy(alpha = 0.5f),
                                    )
                                }
                                innerTextField()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { ftFocused = it.isFocused },
                    )
                    if (state.fullTextQuery.isNotBlank()) {
                        Text(
                            text = "SEARCH",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = TsumoColors.Primary,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            modifier = Modifier
                                .clickable {
                                    focusManager.clearFocus()
                                    onSearchFullText(1)
                                }
                                .padding(start = 10.dp, top = 6.dp, bottom = 6.dp),
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (ftFocused) 1.dp else 0.5.dp)
                        .background(if (ftFocused) TsumoColors.Primary else TsumoColors.Primary.copy(alpha = 0.16f))
                )
                Spacer(modifier = Modifier.height(10.dp))
                SharpSquareCheckbox(
                    checked = state.fullTextPhrase,
                    label = "Exact phrase match",
                    onToggle = onTogglePhrase,
                )
                Spacer(modifier = Modifier.height(12.dp))
                HairlineRule()

                when {
                    state.fullTextLoading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Scanning full-text index…",
                                fontFamily = EBGaramondFamily,
                                fontStyle = FontStyle.Italic,
                                fontSize = 19.sp,
                                color = TsumoColors.Secondary,
                            )
                        }
                    }

                    state.fullTextError != null -> {
                        Text(
                            text = state.fullTextError,
                            style = MaterialTheme.typography.bodySmall.copy(color = TsumoColors.Error),
                            modifier = Modifier.padding(top = 20.dp),
                        )
                    }

                    state.fullTextBooks.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Enter words or a passage to search inside public texts",
                                style = MaterialTheme.typography.bodySmall.copy(color = TsumoColors.Outline),
                            )
                        }
                    }

                    else -> {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 32.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(
                                items = state.fullTextBooks,
                                key = { it.effectiveId.ifBlank { it.url.ifBlank { it.name } } },
                            ) { book ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelectBook(book) }
                                        .padding(vertical = 15.dp),
                                    verticalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    Text(
                                        text = book.name,
                                        fontFamily = EBGaramondFamily,
                                        fontSize = 19.sp,
                                        color = TsumoColors.Primary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            text = listOf(book.authorsDisplay, book.yearDisplay)
                                                .filter { it.isNotBlank() && it != "—" }
                                                .joinToString(" · ")
                                                .ifBlank { "—" },
                                            style = MaterialTheme.typography.bodySmall.copy(color = TsumoColors.Secondary),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text(
                                            text = book.packageDisplay,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = TsumoColors.Secondary.copy(alpha = 0.7f),
                                            ),
                                            modifier = Modifier.padding(start = 12.dp),
                                        )
                                    }
                                }
                                HairlineRule()
                            }
                        }
                    }
                }
            }
        }
    }
}
