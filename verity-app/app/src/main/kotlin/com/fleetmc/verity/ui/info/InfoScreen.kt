package com.fleetmc.verity.ui.info

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fleetmc.verity.BuildConfig
import com.fleetmc.verity.R

@Composable
fun InfoScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.verity_logo),
            contentDescription = stringResource(R.string.logo_description),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(112.dp)
                .clip(RoundedCornerShape(28.dp)),
        )
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Version ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        InfoCard(
            title = "About",
            body = "An offline catalogue of add-ons for Minecraft Bedrock Edition. " +
                "Browse, unlock an add-on with a short video and open it straight in Minecraft.",
        )
        InfoCard(
            title = "Disclaimer",
            body = "This is an unofficial app. It is not affiliated with, endorsed by or connected to " +
                "Mojang Studios or Microsoft. Minecraft is a trademark of Mojang Synergies AB.",
        )
        InfoCard(
            title = "Privacy policy",
            body = AppLinks.PRIVACY_POLICY_URL,
            onClick = { openLink(context, Intent(Intent.ACTION_VIEW, Uri.parse(AppLinks.PRIVACY_POLICY_URL))) },
        )
        InfoCard(
            title = "Contact",
            body = AppLinks.CONTACT_EMAIL,
            onClick = { openLink(context, Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${AppLinks.CONTACT_EMAIL}"))) },
        )
    }
}

@Composable
private fun InfoCard(title: String, body: String, onClick: (() -> Unit)? = null) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    val content: @Composable () -> Unit = {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = if (onClick == null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
        }
    }
    if (onClick == null) {
        Card(Modifier.fillMaxWidth(), MaterialTheme.shapes.large, colors, border = border) { content() }
    } else {
        Card(onClick, Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = colors, border = border) {
            content()
        }
    }
}

private fun openLink(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (error: ActivityNotFoundException) {
        return
    }
}
