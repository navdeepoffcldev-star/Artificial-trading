package com.example

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay

class MyLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    init {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun destroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }
}

class TradeBotService : AccessibilityService() {
    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var myLifecycleOwner: MyLifecycleOwner? = null
    private lateinit var windowLayoutParams: WindowManager.LayoutParams

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        myLifecycleOwner = MyLifecycleOwner()
        
        windowLayoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 100
        }

        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(myLifecycleOwner)
            setViewTreeViewModelStoreOwner(myLifecycleOwner)
            setViewTreeSavedStateRegistryOwner(myLifecycleOwner)
            setContent {
                MyApplicationTheme(darkTheme = true) {
                    AITradeSystemOverlay(
                        onDrag = { dx, dy ->
                            windowLayoutParams.x += dx.toInt()
                            windowLayoutParams.y += dy.toInt()
                            windowManager?.updateViewLayout(this@apply, windowLayoutParams)
                        },
                        onClose = {
                            // Can be used to close/hide the bot UI
                        }
                    )
                }
            }
        }
        
        windowManager?.addView(composeView, windowLayoutParams)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!BotState.isRunning.value) return
        val root = rootInActiveWindow ?: return
        
        val target = BotState.selectedAsset.value
        
        // Find if target asset is on screen
        val assetNodes = root.findAccessibilityNodeInfosByText(target)
        if (assetNodes.isNullOrEmpty()) {
            BotState.statusMessage.value = "Waiting for $target to appear on screen..."
            return
        }

        BotState.statusMessage.value = "Asset $target detected!"
        
        // Find "Buy" or "Sell" buttons on screen
        val buyNodes = root.findAccessibilityNodeInfosByText("Buy")
        val sellNodes = root.findAccessibilityNodeInfosByText("Sell")
        
        if (!buyNodes.isNullOrEmpty() || !sellNodes.isNullOrEmpty()) {
            // In a real dangerous bot, we would do: buyNodes[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
            // But for safety, we just log the simulated trade since we detected the real buttons
            
            val currentTime = System.currentTimeMillis()
            if (currentTime - BotState.lastTradeTime > 3000) { // Cooldown between simulated trades
                BotState.trades.value += 1
                BotState.profit.value += (-5..15).random().toFloat()
                BotState.lastTradeTime = currentTime
            }
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        composeView?.let { windowManager?.removeView(it) }
        myLifecycleOwner?.destroy()
    }
}

object BotState {
    val isExpanded = mutableStateOf(false)
    val isRunning = mutableStateOf(false)
    val profit = mutableFloatStateOf(0f)
    val trades = mutableIntStateOf(0)
    val isSelectingAsset = mutableStateOf(false)
    val selectedAsset = mutableStateOf("Vodafone Idea")
    val statusMessage = mutableStateOf("Idle")
    var lastTradeTime: Long = 0
}

@Composable
fun AITradeSystemOverlay(onDrag: (Float, Float) -> Unit, onClose: () -> Unit) {
    val isExpanded by BotState.isExpanded
    val isRunning by BotState.isRunning
    val profit by BotState.profit
    val trades by BotState.trades
    val isSelectingAsset by BotState.isSelectingAsset
    val selectedAsset by BotState.selectedAsset
    val statusMessage by BotState.statusMessage
    
    val targetAssets = listOf("Vodafone Idea", "Bharti Airtel", "Reliance Ind.", "Tata Motors", "BTC/USD", "Buy", "Sell")

    Box(modifier = Modifier.padding(16.dp)) {
        if (isExpanded) {
            Card(
                modifier = Modifier.width(320.dp),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Analytics, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("AITrade Bot", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = { BotState.isExpanded.value = false; BotState.isSelectingAsset.value = false }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "Minimize")
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Mandatory Safety Banner for Financial Simulation
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF5D4037)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "SAFETY SIMULATION MODE:\nReads real screen elements. Real clicking is disabled to prevent financial loss.",
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (isSelectingAsset && !isRunning) {
                        Text("Select Target Asset:", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            targetAssets.forEach { asset ->
                                Text(
                                    text = asset,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { 
                                            BotState.selectedAsset.value = asset
                                            BotState.isSelectingAsset.value = false 
                                        }
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (asset == selectedAsset) FontWeight.Bold else FontWeight.Normal,
                                    color = if (asset == selectedAsset) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    } else {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Target Asset:", style = MaterialTheme.typography.bodyMedium)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.clickable(enabled = !isRunning) { BotState.isSelectingAsset.value = true }
                            ) {
                                Text(
                                    text = selectedAsset,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Screen Status:", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                if (isRunning) statusMessage else "Idle", 
                                color = if (isRunning) Color(0xFF4CAF50) else Color(0xFFF44336),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.End
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Session Profit:", style = MaterialTheme.typography.bodyMedium)
                            Text(String.format("+$%.2f", profit), color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Trades Executed:", style = MaterialTheme.typography.bodyMedium)
                            Text("$trades", fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Button(
                        onClick = { 
                            BotState.isRunning.value = !isRunning 
                            BotState.isSelectingAsset.value = false 
                            if (!isRunning) BotState.statusMessage.value = "Waiting for screen data..."
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRunning) Color(0xFFF44336) else Color(0xFF4CAF50)
                        )
                    ) {
                        Icon(
                            if (isRunning) Icons.Filled.Stop else Icons.Filled.PlayArrow, 
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isRunning) "Stop Automation" else "Start Automation")
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable { BotState.isExpanded.value = true }
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount.x, dragAmount.y)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.TrendingUp, 
                    contentDescription = "Open AITrade", 
                    tint = Color.White, 
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}
