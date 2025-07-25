package com.prashik.firewallapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prashik.firewallapp.R
import com.prashik.firewallapp.data.local.modal.BlockLogEntity

@Composable
fun TrafficLog_Item(
    blockLogEntity: BlockLogEntity,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = colorResource(R.color.card_container),
            contentColor = colorResource(R.color.white)
        ),
        shape = RoundedCornerShape(6.dp),
        modifier = modifier
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Custom_Bold_Text(
                text = blockLogEntity.appName
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Custom_Bold_Text(
                    text = blockLogEntity.protocol,

                    )
                Text(
                    text = blockLogEntity.timestamp,
                    fontSize = 16.sp
                )
            }
            Custom_Bold_Text(
                unBoldText = "src  ",
                text = "${blockLogEntity.srcIp} (${blockLogEntity.srcPort})"
            )
            Custom_Bold_Text(
                unBoldText = "dst  ",
                text = "${blockLogEntity.dstIp} (${blockLogEntity.dstPort})"
            )
        }
    }
}