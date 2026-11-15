package com.tsumo.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tsumo.app.TsumoUiState
import com.tsumo.app.data.BookItem
import com.tsumo.app.ui.components.ArchivalCoverPlate
import com.tsumo.app.ui.components.EditorialInputField
import com.tsumo.app.ui.components.FilterTabItem
import com.tsumo.app.ui.components.HairlineRule
import com.tsumo.app.ui.components.PrimaryTypographicAction
import com.tsumo.app.ui.components.SharpSquareCheckbox
import com.tsumo.app.ui.theme.EBGaramondFamily
import com.tsumo.app.ui.theme.TsumoColors

private val ExtFilters = listOf("ALL", "EPUB", "PDF", "MOBI", "AZW3")
private val ArchivalSeeds = listOf(
    "Norwegian Wood",
    "The Old Man and the Sea",
    "No Longer Human",
    "The Metamorphosis",
    "Meditations",
    "The Book of Tea",
)

@Composable
fun DiscoverScreen(
    state: TsumoUiState,
    onQueryChange: (String) -> Unit,
    onSearch: (String?, Int) -> Unit,
    onSelectExt: (String) -> Unit,
    onUpdateAdvancedFilters: (String, String, String, Boolean) -> Unit,
    onSelectBook: (BookItem) -> Unit,
    onOpenAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    var searchFocused by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TsumoColors.Surface)
            .padding(horizontal = 24.dp),
    ) {
        // Seamless Canvas Search Field
        Column(modifier = Modifier.padding(top = 8.dp, bottom = 20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = state.searchQuery,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    cursorBrush = SolidColor(TsumoColors.Primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            focusManager.clearFocus()
                            onSearch(state.searchQuery, 1)
                        }
                    ),
                    textStyle = TextStyle(
                        fontFamily = EBGaramondFamily,
                        fontSize = 21.sp,
                        lineHeight = 28.sp,
                        color = TsumoColors.Primary,
                        letterSpacing = 0.2.sp,
                    ),
                    decorationBox = { innerTextField ->
                        Box(modifier = Modifier.padding(vertical = 8.dp)) {
                            if (state.searchQuery.isEmpty()) {
                                Text(
                                    text = "Search book...",
                                    fontFamily = EBGaramondFamily,
                                    fontSize = 21.sp,
                                    lineHeight = 28.sp,
                                    color = TsumoColors.Secondary.copy(alpha = 0.50f),
                                )
                            }
                            innerTextField()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { searchFocused = it.isFocused },
                )

                if (state.searchQuery.isNotBlank()) {
                    Text(
                        text = "SEARCH",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TsumoColors.Primary,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.2.sp,
                        ),
                        modifier = Modifier
                            .clickable {
                                focusManager.clearFocus()
                                onSearch(state.searchQuery, 1)
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                    Text(
                        text = "CLEAR",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TsumoColors.Secondary,
                            letterSpacing = 1.2.sp,
                        ),
                        modifier = Modifier
                            .clickable {
                                onQueryChange("")
                                onSearch("", 1)
                            }
                            .padding(start = 4.dp, top = 6.dp, bottom = 6.dp),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (searchFocused || state.searchQuery.isNotBlank()) 1.dp else 0.5.dp)
                    .background(
                        if (searchFocused) TsumoColors.Primary else TsumoColors.Primary.copy(alpha = 0.15f)
                    )
            )
        }

        // Minimalist Filter Categorization
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                ExtFilters.forEach { ext ->
                    FilterTabItem(
                        label = ext,
                        selected = state.selectedExtFilter == ext,
                        onClick = { onSelectExt(ext) },
                    )
                }
            }
            Text(
                text = if (showFilters) "HIDE" else "REFINE",
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 1.2.sp,
                    color = if (showFilters || state.filterLanguage.isNotBlank() || state.filterFromYear.isNotBlank() || state.filterToYear.isNotBlank() || state.filterExact) {
                        TsumoColors.Primary
                    } else {
                        TsumoColors.Secondary.copy(alpha = 0.75f)
                    },
                ),
                modifier = Modifier
                    .clickable { showFilters = !showFilters }
                    .padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
            )
        }

        // Expandable Refine Panel
        AnimatedVisibility(visible = showFilters) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    EditorialInputField(
                        value = state.filterLanguage,
                        onValueChange = {
                            onUpdateAdvancedFilters(it, state.filterFromYear, state.filterToYear, state.filterExact)
                        },
                        placeholder = "english, japanese...",
                        label = "Language",
                        modifier = Modifier.weight(1.4f),
                    )
                    EditorialInputField(
                        value = state.filterFromYear,
                        onValueChange = {
                            onUpdateAdvancedFilters(state.filterLanguage, it, state.filterToYear, state.filterExact)
                        },
                        placeholder = "1900",
                        label = "From",
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(0.8f),
                    )
                    EditorialInputField(
                        value = state.filterToYear,
                        onValueChange = {
                            onUpdateAdvancedFilters(state.filterLanguage, state.filterFromYear, it, state.filterExact)
                        },
                        placeholder = "2026",
                        label = "To",
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(0.8f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SharpSquareCheckbox(
                        checked = state.filterExact,
                        label = "Exact match",
                        onToggle = {
                            onUpdateAdvancedFilters(
                                state.filterLanguage,
                                state.filterFromYear,
                                state.filterToYear,
                                !state.filterExact,
                            )
                        },
                    )
                    if (state.searchQuery.isNotBlank()) {
                        Text(
                            text = "APPLY",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = TsumoColors.Primary,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            modifier = Modifier
                                .clickable {
                                    showFilters = false
                                    onSearch(state.searchQuery, 1)
                                }
                                .padding(vertical = 6.dp),
                        )
                    }
                }
                HairlineRule()
            }
        }

        // Main Body
        when {
            !state.status.loggedIn && state.authLoading && state.discoverBooks.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 64.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Connecting to archive…",
                        fontFamily = EBGaramondFamily,
                        fontStyle = FontStyle.Italic,
                        fontSize = 19.sp,
                        color = TsumoColors.Secondary,
                    )
                }
            }

            !state.status.loggedIn && state.discoverBooks.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 36.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = "Archive session required",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontStyle = FontStyle.Italic,
                            color = TsumoColors.Primary,
                        ),
                    )
                    Text(
                        text = "Sign in with your Z-Library credentials or import session cookies to search, inspect records, and download editions directly to your device.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = TsumoColors.OnSurfaceVariant,
                        ),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    PrimaryTypographicAction(
                        label = "Open account & sign in",
                        trailing = state.status.domain.removePrefix("https://"),
                        onClick = onOpenAccount,
                    )
                }
            }

            state.discoverLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 64.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Querying archive…",
                        fontFamily = EBGaramondFamily,
                        fontStyle = FontStyle.Italic,
                        fontSize = 19.sp,
                        color = TsumoColors.Secondary,
                    )
                }
            }

            state.discoverError != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Archive request interrupted",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontStyle = FontStyle.Italic,
                            color = TsumoColors.Error,
                        ),
                    )
                    Text(
                        text = state.discoverError,
                        style = MaterialTheme.typography.bodySmall.copy(color = TsumoColors.OnSurfaceVariant),
                    )
                    PrimaryTypographicAction(
                        label = "Retry query",
                        onClick = { onSearch(state.activeSearchQuery, state.discoverPage) },
                    )
                }
            }

            state.activeSearchQuery.isBlank() && state.discoverBooks.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    Text(
                        text = "Suggested Folios",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.4.sp,
                            color = TsumoColors.Outline,
                        ),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        ArchivalSeeds.forEach { seed ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSearch(seed, 1) }
                                    .padding(vertical = 12.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = seed,
                                        fontFamily = EBGaramondFamily,
                                        fontSize = 20.sp,
                                        color = TsumoColors.Primary,
                                    )
                                    Text(
                                        text = "QUERY",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = TsumoColors.Secondary.copy(alpha = 0.7f),
                                        ),
                                    )
                                }
                            }
                            HairlineRule()
                        }
                    }
                }
            }

            state.discoverBooks.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 72.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "No folio entries found",
                        fontFamily = EBGaramondFamily,
                        fontStyle = FontStyle.Italic,
                        fontSize = 20.sp,
                        color = TsumoColors.Secondary,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Review query or adjust format filters",
                        style = MaterialTheme.typography.bodySmall.copy(color = TsumoColors.Outline),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    contentPadding = PaddingValues(bottom = 32.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(
                        items = state.discoverBooks,
                        key = { b -> b.effectiveId.ifBlank { b.url.ifBlank { b.name + b.size } } },
                    ) { book ->
                        BookPosterGridItem(
                            book = book,
                            onClick = { onSelectBook(book) },
                        )
                    }

                    if (state.discoverTotalPages > 1) {
                        item(span = { GridItemSpan(2) }) {
                            Column(modifier = Modifier.padding(top = 8.dp)) {
                                HairlineRule()
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "PREVIOUS",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = if (state.discoverPage > 1) TsumoColors.Primary else TsumoColors.OutlineVariant,
                                            letterSpacing = 1.2.sp,
                                        ),
                                        modifier = Modifier
                                            .clickable(enabled = state.discoverPage > 1) {
                                                onSearch(state.activeSearchQuery, state.discoverPage - 1)
                                            }
                                            .padding(vertical = 8.dp),
                                    )
                                    Text(
                                        text = "PAGE ${state.discoverPage} OF ${state.discoverTotalPages}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = TsumoColors.Secondary,
                                            letterSpacing = 1.2.sp,
                                        ),
                                    )
                                    Text(
                                        text = "NEXT",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = if (state.discoverPage < state.discoverTotalPages) TsumoColors.Primary else TsumoColors.OutlineVariant,
                                            letterSpacing = 1.2.sp,
                                        ),
                                        modifier = Modifier
                                            .clickable(enabled = state.discoverPage < state.discoverTotalPages) {
                                                onSearch(state.activeSearchQuery, state.discoverPage + 1)
                                            }
                                            .padding(vertical = 8.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BookPosterGridItem(
    book: BookItem,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        ArchivalCoverPlate(
            coverUrl = book.cover,
            title = book.name,
            authors = book.authorsDisplay,
            extension = book.extension,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = book.yearDisplay,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = TsumoColors.Secondary,
                    letterSpacing = 1.0.sp,
                ),
            )
            Text(
                text = book.packageDisplay,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = TsumoColors.Secondary.copy(alpha = 0.65f),
                    letterSpacing = 0.6.sp,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = book.name,
            fontFamily = EBGaramondFamily,
            fontSize = 15.sp,
            lineHeight = 19.sp,
            color = TsumoColors.Primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (book.authorsDisplay.isNotBlank()) {
            Text(
                text = book.authorsDisplay,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp,
                    color = TsumoColors.Outline,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
