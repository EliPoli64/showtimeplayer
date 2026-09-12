package com.showtimeplayer.ui.screens.presets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.showtimeplayer.data.db.entity.MetronomeRegion
import com.showtimeplayer.data.repository.MetronomeLayerWithRegions

@Composable
fun MetronomeTrackRow(
    layerWithRegions: MetronomeLayerWithRegions,
    colorIndex: Int,
    selectedRegionId: Long?,
    trackDurationMs: Long,
    onRegionTap: (MetronomeRegion) -> Unit,
    onAddRegion: () -> Unit,
    onRemove: () -> Unit,
    onToggleEnabled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        LayerHeader(
            layer = layerWithRegions.layer,
            colorIndex = colorIndex,
            onToggleEnabled = onToggleEnabled,
            onRemove = onRemove,
            onAddRegion = onAddRegion,
            regionCount = layerWithRegions.regions.size,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .padding(start = 56.dp, end = 8.dp),
        ) {
            layerWithRegions.regions.forEach { region ->
                RegionBlock(
                    region = region,
                    colorIndex = colorIndex,
                    trackDurationMs = trackDurationMs,
                    isSelected = region.id == selectedRegionId,
                    onTap = { onRegionTap(region) },
                )
            }
        }
    }
}
