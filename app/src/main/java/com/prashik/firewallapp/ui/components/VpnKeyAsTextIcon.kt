package com.prashik.firewallapp.ui.components

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.sp

@Composable
fun VpnKeyAsTextIcon(
    modifier: Modifier = Modifier
) {
    val myId = "vpnKeyIcon"
    val text = buildAnnotatedString {
        appendInlineContent(myId, "[icon]")
        append(" appears at the top of your screen when VPN is active.")
    }

    val inlineContent = mapOf(
        myId to InlineTextContent(
            placeholder = androidx.compose.ui.text.Placeholder(
                width = 20.sp,
                height = 20.sp,
                placeholderVerticalAlign = PlaceholderVerticalAlign.Center
            )
        ) {
            Icon(
                imageVector = Icons.Outlined.VpnKey,
                contentDescription = null
            )
        }
    )

    Text(
        text = text,
        inlineContent = inlineContent,
        color = Color.DarkGray,
        fontSize = 18.sp,
        modifier = modifier
    )
}