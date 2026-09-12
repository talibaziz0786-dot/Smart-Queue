package com.example.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.SQButton
import com.example.ui.components.SQButtonVariant
import com.example.ui.components.SQCard
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LandingMarketingScreen(
    onBack: () -> Unit,
    onGetStarted: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SmartQueue Platform") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 48.dp)
        ) {
            // Hero Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        PrimaryIndigoDark,
                                        MaterialTheme.colorScheme.surface
                                    )
                                )
                            )
                            .padding(24.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                color = PrimaryCyan.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Text(
                                    text = "AI-POWERED DIGITAL QUEUE SAAS",
                                    color = PrimaryCyan,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "Stop Waiting.\nKnow Your Turn.",
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = (-0.5).sp,
                                    lineHeight = 38.sp
                                ),
                                textAlign = TextAlign.Center,
                                color = Color.White
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Eliminate physical crowds with digital token passes, real-time counter updates, and multi-location queue orchestration.",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = Color.White.copy(alpha = 0.8f)
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            SQButton(
                                text = "Launch Live Application",
                                onClick = onGetStarted,
                                modifier = Modifier.fillMaxWidth(),
                                icon = Icons.Default.RocketLaunch
                            )
                        }
                    }
                }
            }

            // Key Value Pillars
            item {
                Text(
                    text = "Engineered For Trust & Scale",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }

            item {
                MarketingFeatureCard(
                    icon = Icons.Default.QrCodeScanner,
                    title = "Scan & Join Instantly",
                    description = "Customers scan branch standee QR codes and receive digital tokens immediately without app friction.",
                    color = PrimaryCyan
                )
            }

            item {
                MarketingFeatureCard(
                    icon = Icons.Default.Bolt,
                    title = "Strict Atomic Concurrency",
                    description = "Guarantees zero duplicate token sequence assignment even under 100 simultaneous concurrent joins.",
                    color = AccentEmerald
                )
            }

            item {
                MarketingFeatureCard(
                    icon = Icons.Default.Shield,
                    title = "Built-in Abuse Prevention",
                    description = "Automated 3-strike policies detect phantom accounts and repeated no-shows to protect real waiting customers.",
                    color = AccentAmber
                )
            }

            item {
                MarketingFeatureCard(
                    icon = Icons.Default.AccountTree,
                    title = "Multi-Location Multi-Counter",
                    description = "Single business dashboard managing distributed city branches, specialized service lines, and operator counters.",
                    color = PrimaryIndigo
                )
            }
        }
    }
}

@Composable
fun MarketingFeatureCard(
    icon: ImageVector,
    title: String,
    description: String,
    color: Color
) {
    SQCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(color.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
