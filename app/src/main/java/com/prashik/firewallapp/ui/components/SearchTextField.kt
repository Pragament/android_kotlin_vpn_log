package com.prashik.firewallapp.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

@Composable
fun Search_Text_Field(
    searchQuery: String,
    onIsSearchingChange: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    TextField(
        value = searchQuery,
        onValueChange = { newSearchQuery -> onSearchQueryChange(newSearchQuery) },
        trailingIcon = {
            IconButton(
                onClick = { onSearchQueryChange("") }
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close"
                )
            }
        },
        leadingIcon = {
            IconButton(
                onClick = {
                    onIsSearchingChange(false)
                    onSearchQueryChange("")
                }
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Nav back"
                )
            }
        },
        placeholder = {
            Text(
                text = "Start searching...",
                color = Color.Black,
                fontWeight = FontWeight.Bold
            )
        },
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
    )
}