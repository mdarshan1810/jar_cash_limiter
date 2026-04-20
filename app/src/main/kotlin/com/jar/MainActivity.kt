package com.jar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jar.ui.jar.JarScreen
import com.jar.ui.jar.JarViewModel
import com.jar.ui.theme.JarTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as JarApp).container
        setContent {
            JarTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val jarViewModel: JarViewModel = viewModel(
                        factory = JarViewModel.factory(container)
                    )
                    JarScreen(viewModel = jarViewModel)
                }
            }
        }
    }
}
