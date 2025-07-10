package com.prashik.firewallapp.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Composable
fun Custom_Bold_Text(
    modifier: Modifier = Modifier,
    unBoldText: String = "",
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (unBoldText.isNotEmpty()) {
            Text(
                text = unBoldText,
                fontSize = 17.sp
            )
        }
        Text(
            text = text,
            fontWeight = FontWeight.W700,
            fontSize = 18.sp,
            modifier = modifier
        )
    }
}