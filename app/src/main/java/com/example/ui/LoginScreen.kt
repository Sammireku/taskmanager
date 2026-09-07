package com.example.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("Welcome to Cobbyai")
        Button(onClick = onLoginSuccess) {
            Text("Sign in with Google")
        }
    }
}
