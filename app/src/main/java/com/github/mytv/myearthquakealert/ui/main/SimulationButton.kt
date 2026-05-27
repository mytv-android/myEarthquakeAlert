package com.github.mytv.myearthquakealert.ui.main

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.github.mytv.myearthquakealert.R
import com.github.mytv.myearthquakealert.ui.adaptive.handleUserKey
import com.github.mytv.myearthquakealert.ui.theme.AlertRed

@Composable
fun SimulationButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = AlertRed),
        modifier = modifier
            .fillMaxWidth()
            .handleUserKey { onClick() },
    ) {
        Text(text = stringResource(R.string.simulation_test))
    }
}
