package com.kiroku.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kiroku.app.data.LinkEntity
import com.kiroku.app.data.NoteEntity

@Composable
fun LinkGrid(
    links: List<LinkEntity>,
    gridLayout: Boolean,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** When set, each card shows which folder the link is in. */
    folderOf: ((LinkEntity) -> Pair<String, FolderIcon>)? = null,
) {
    LazyVerticalStaggeredGrid(
        columns = if (gridLayout) StaggeredGridCells.Adaptive(160.dp) else StaggeredGridCells.Fixed(1),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 104.dp),
        verticalItemSpacing = 10.dp,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        items(links, key = { it.id }) { link ->
            LinkCard(item = link, onClick = { onOpenLink(link.id) }, folder = folderOf?.invoke(link))
        }
    }
}

@Composable
fun NoteGrid(
    notes: List<NoteEntity>,
    onOpenNote: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(160.dp),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 104.dp),
        verticalItemSpacing = 10.dp,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        items(notes, key = { it.id }) { note ->
            NoteCard(note = note, onClick = { onOpenNote(note.id) })
        }
    }
}
