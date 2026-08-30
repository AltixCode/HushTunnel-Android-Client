package com.v2ray.ang.ui.brand

import android.content.Intent
import android.os.Bundle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.v2ray.ang.ui.base.BaseComponentActivity

/**
 * The app's launcher Activity: routes straight to Home (already logged in)
 * or Login — the user never sees v2rayNG's own server-list MainActivity,
 * which is no longer reachable from the manifest's launcher intent-filter.
 */
class SplashActivity : BaseComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val destination = if (AuthStore.isLoggedIn()) HomeActivity::class.java else LoginActivity::class.java
        startActivity(Intent(this, destination))
        finish()
    }

    @Composable
    override fun ScreenContent() {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}
