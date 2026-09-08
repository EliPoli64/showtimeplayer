package com.showtimeplayer.ui.screens.presets

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.showtimeplayer.data.db.entity.TrackEntity

data class TimeSignature(val num: Int, val denom: Int, val label: String)

private val commonSignatures = listOf(
    TimeSignature(4, 4, "4/4"),
    TimeSignature(3, 4, "3/4"),
    TimeSignature(6, 8, "6/8"),
    TimeSignature(2, 4, "2/4"),
    TimeSignature(5, 4, "5/4"),
    TimeSignature(7, 8, "7/8"),
    TimeSignature(4, 4, "Common Time"),
    TimeSignature(3, 4, "Waltz"),
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddPresetTimeSignatureScreen(
    selectedTrack: TrackEntity?,
    currentTimeSigNum: Int,
    currentTimeSigDenom: Int,
    onTimeSignatureChanged: (num: Int, denom: Int) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Time Signature") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Track info
            Text(
                text = selectedTrack?.title ?: "",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Select time signature",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Current: $currentTimeSigNum/$currentTimeSigDenom",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(24.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                commonSignatures.distinctBy { "${it.num}/${it.denom}" }.forEach { sig ->
                    val isSelected = sig.num == currentTimeSigNum && sig.denom == currentTimeSigDenom
                    val borderColor = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    }

                    Text(
                        text = sig.label,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
                            .clickable { onTimeSignatureChanged(sig.num, sig.denom) }
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        textAlign = TextAlign.Center,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Next")
            }
        }
    }
}
