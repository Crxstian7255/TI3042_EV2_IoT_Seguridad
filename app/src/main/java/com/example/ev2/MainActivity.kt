package com.example.ev2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {

    private lateinit var dbHelper: DatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dbHelper = DatabaseHelper(this)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var loggedUser by remember { mutableStateOf<String?>(null) }

                    if (loggedUser == null) {
                        LoginScreen(
                            dbHelper = dbHelper,
                            onLoginSuccess = { user ->
                                loggedUser = user
                            }
                        )
                    } else {
                        MainIoTScreen(
                            dbHelper = dbHelper,
                            currentUser = loggedUser ?: "admin",
                            onLogout = { loggedUser = null }
                        )
                    }
                }
            }
        }
    }
}