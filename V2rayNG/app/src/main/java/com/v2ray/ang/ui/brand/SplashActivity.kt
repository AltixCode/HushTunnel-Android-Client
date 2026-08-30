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
 * The app's launcher Activity: routes to ResellerHome (reseller logged in),
 * Home (user logged in), or Login — rejects admin sessions.
 */
class SplashActivity : BaseComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val destination = if (AuthStore.isLoggedIn()) {
            val role = AuthStore.getRole()
            when {
                role.equals("RESELLER", ignoreCase = true) -> ResellerHomeActivity::class.java
                role.equals("ADMIN", ignoreCase = true) -> {
                    AuthStore.clear()
                    LoginActivity::class.java
                }
                else -> HomeActivity::class.java
            }
        } else {
            LoginActivity::class.java
        }
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
