package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.tooling.preview.Preview
import com.example.CobbyaiApp
import com.example.ui.HomeScreen
import com.example.ui.TaskViewModel
import com.example.ui.theme.CobbyaiTheme
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.ui.TaskDetailScreen
import com.example.ui.TaskFormScreen
import com.example.data.Task

import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.navDeepLink
import android.content.Intent

class MainActivity : ComponentActivity() {
  private val openCreateTask = mutableStateOf(false)
  private val activeTaskId = mutableStateOf<Int?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val app = application as CobbyaiApp
    val viewModel = TaskViewModel(app.database, this)

    handleIncomingIntent(intent)
    
    enableEdgeToEdge()
    setContent {
      val themeMode by viewModel.themeMode.collectAsState()
      val dynamicColor by viewModel.dynamicColorEnabled.collectAsState()

      CobbyaiTheme(themeMode = themeMode, dynamicColor = dynamicColor) {
        val navController = rememberNavController()

        val currentTaskId by activeTaskId
        val shouldCreate by openCreateTask

        LaunchedEffect(currentTaskId) {
          currentTaskId?.let { id ->
            navController.navigate("detail/$id")
            activeTaskId.value = null
          }
        }

        LaunchedEffect(shouldCreate) {
          if (shouldCreate) {
            navController.navigate("create")
            openCreateTask.value = false
          }
        }

        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
          NavHost(navController = navController, startDestination = "home") {
            composable("home") {
              HomeScreen(
                viewModel = viewModel,
                onTaskClick = { taskId -> navController.navigate("detail/$taskId") },
                onEditTask = { taskId -> navController.navigate("edit/$taskId") },
                onCreateTask = { navController.navigate("create") },
                modifier = Modifier.padding(innerPadding)
              )
            }
            composable("create") {
              TaskFormScreen(
                taskId = null,
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
              )
            }
            composable("edit/{taskId}") { backStackEntry ->
              val taskId = backStackEntry.arguments?.getString("taskId")?.toIntOrNull()
              TaskFormScreen(
                taskId = taskId,
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
              )
            }
            composable(
              route = "detail/{taskId}",
              deepLinks = listOf(
                navDeepLink { uriPattern = "cobbyai://task/{taskId}" }
              )
            ) { backStackEntry ->
              val taskId = backStackEntry.arguments?.getString("taskId")?.toIntOrNull() ?: 0
              TaskDetailScreen(
                taskId = taskId,
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onEditTask = { id -> navController.navigate("edit/$id") }
              )
            }
          }
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleIncomingIntent(intent)
  }

  private fun handleIncomingIntent(intent: Intent?) {
    if (intent == null) return
    if (intent.getBooleanExtra("EXTRA_OPEN_CREATE_TASK", false)) {
      openCreateTask.value = true
    }
    val id = extractTaskIdFromIntent(intent)
    if (id != null) {
      activeTaskId.value = id
    }
  }

  private fun extractTaskIdFromIntent(intent: Intent?): Int? {
    if (intent == null) return null
    if (intent.hasExtra("taskId")) {
      val id = intent.getIntExtra("taskId", -1)
      if (id != -1) return id
    }
    val uri = intent.data
    if (uri != null && uri.scheme == "cobbyai" && uri.host == "task") {
      val pathPart = uri.lastPathSegment ?: uri.path?.trim('/')
      return pathPart?.toIntOrNull()
    }
    return null
  }
}

