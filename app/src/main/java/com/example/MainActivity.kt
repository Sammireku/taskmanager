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
  private var viewModel: TaskViewModel? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val app = application as CobbyaiApp
    val vm = TaskViewModel(app.database, this)
    this.viewModel = vm

    handleIncomingIntent(intent)
    
    enableEdgeToEdge()
    setContent {
      val themeMode by vm.themeMode.collectAsState()
      val dynamicColor by vm.dynamicColorEnabled.collectAsState()
      val isOnboardingCompleted by vm.isOnboardingCompleted.collectAsState()
      val startDestination = if (!isOnboardingCompleted) "onboarding" else "home"

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
          NavHost(navController = navController, startDestination = startDestination) {
            composable("onboarding") {
              com.example.ui.OnboardingPermissionsScreen(
                viewModel = vm,
                onOnboardingComplete = {
                  navController.navigate("home") {
                    popUpTo("onboarding") { inclusive = true }
                  }
                }
              )
            }
            composable("home") {
              HomeScreen(
                viewModel = vm,
                onTaskClick = { taskId -> navController.navigate("detail/$taskId") },
                onEditTask = { taskId -> navController.navigate("edit/$taskId") },
                onCreateTask = { navController.navigate("create") },
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToDiagnostics = { navController.navigate("geofence_diagnostics") },
                modifier = Modifier.padding(innerPadding)
              )
            }
            composable("geofence_diagnostics") {
              com.example.ui.GeofenceDiagnosticScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() }
              )
            }
            composable("settings") {
              com.example.ui.SettingsScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onNavigateToDiagnostics = { navController.navigate("geofence_diagnostics") }
              )
            }
            composable("create") {
              TaskFormScreen(
                taskId = null,
                viewModel = vm,
                onBack = { navController.popBackStack() }
              )
            }
            composable("edit/{taskId}") { backStackEntry ->
              val taskId = backStackEntry.arguments?.getString("taskId")?.toIntOrNull()
              TaskFormScreen(
                taskId = taskId,
                viewModel = vm,
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
                viewModel = vm,
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

    // Check for Google Assistant App Action queries or shared voice text
    val assistantQuery = extractAssistantQuery(intent)
    if (!assistantQuery.isNullOrBlank()) {
      viewModel?.parseAndAddTask(assistantQuery, isVoiceInitiated = true)
      return
    }

    // Check for voice shortcut launcher (e.g. cobbyai://voice)
    val uri = intent.data
    if (uri != null && uri.scheme == "cobbyai" && uri.host == "voice") {
      viewModel?.triggerVoiceInput()
      return
    }

    if (intent.getBooleanExtra("EXTRA_OPEN_CREATE_TASK", false)) {
      openCreateTask.value = true
    }
    val id = extractTaskIdFromIntent(intent)
    if (id != null) {
      activeTaskId.value = id
    }
  }

  private fun extractAssistantQuery(intent: Intent?): String? {
    if (intent == null) return null

    // 1. Check intent parameters defined in shortcuts.xml
    if (intent.hasExtra("assistant_query")) {
      val query = intent.getStringExtra("assistant_query")
      if (!query.isNullOrBlank()) return query
    }

    // 2. Check standard Google Assistant BII extras
    val extras = intent.extras
    if (extras != null) {
      for (key in listOf("text", "query", "android.intent.extra.TEXT", "taskList.name", "note.text")) {
        val value = extras.getString(key)
        if (!value.isNullOrBlank()) return value
      }
    }

    // 3. Check URI query parameters (e.g. cobbyai://task?query=... or cobbyai://voice?text=...)
    val uri = intent.data
    if (uri != null) {
      val textParam = uri.getQueryParameter("text")
        ?: uri.getQueryParameter("query")
        ?: uri.getQueryParameter("q")
      if (!textParam.isNullOrBlank()) return textParam
    }

    return null
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

