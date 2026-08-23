package com.lazysimulation.freecasts

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.lazysimulation.freecasts.ui.screens.MainScreen
import com.lazysimulation.freecasts.ui.theme.FreeCastsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val navController = rememberNavController()
            FreeCastsTheme {
                MainScreen(
                    modifier = Modifier,
                    navController = navController
                )
            }
        }
    }
}