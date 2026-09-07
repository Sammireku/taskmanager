package com.example.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

class FirestoreRepository {
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }

    fun getUserId(): String {
        return auth.currentUser?.uid ?: "device_user_default"
    }

    suspend fun saveTask(task: Task, userId: String = getUserId()) {
        try {
            val userDoc = firestore.collection("users").document(userId).collection("userTasks").document(task.id.toString())
            val taskMap = hashMapOf<String, Any?>(
                "id" to task.id,
                "title" to task.safeTitle,
                "description" to task.description,
                "priority" to task.safePriority,
                "dueDate" to task.dueDate,
                "completionStatus" to task.completionStatus,
                "status" to task.safeStatus,
                "isHabit" to task.isHabit,
                "habitFrequency" to task.habitFrequency,
                "isCompleted" to task.isDone,
                "category" to task.safeCategory,
                "subtasksJson" to task.subtasksJson,
                "locationName" to task.locationName,
                "latitude" to task.latitude,
                "longitude" to task.longitude,
                "geofenceRadius" to task.geofenceRadius,
                "triggerDirection" to task.safeTriggerDirection,
                "reminderTone" to task.safeReminderTone,
                "isDeleted" to task.isDeleted,
                "deletedAt" to task.deletedAt
            )
            userDoc.set(taskMap, SetOptions.merge()).await()
        } catch (e: Exception) {
            // Log or ignore if offline mode
        }
    }

    suspend fun syncTasks(localTasks: List<Task>, userId: String = getUserId()): List<Task> {
        // Push local tasks
        for (t in localTasks) {
            saveTask(t, userId)
        }

        // Pull remote tasks from Firestore
        val snapshot = firestore.collection("users").document(userId).collection("userTasks").get().await()
        val remoteList = mutableListOf<Task>()
        for (doc in snapshot.documents) {
            try {
                val id = doc.getLong("id")?.toInt() ?: continue
                val title = doc.getString("title") ?: "Synced Task"
                val description = doc.getString("description")
                val priority = doc.getString("priority") ?: "Medium"
                val dueDate = doc.getLong("dueDate")
                val completionStatus = doc.getString("completionStatus") ?: "PENDING"
                val isHabit = doc.getBoolean("isHabit") ?: false
                val habitFrequency = doc.getString("habitFrequency")
                val isCompleted = doc.getBoolean("isCompleted") ?: false
                val category = doc.getString("category")
                val subtasksJson = doc.getString("subtasksJson")
                val locationName = doc.getString("locationName")
                val latitude = doc.getDouble("latitude")
                val longitude = doc.getDouble("longitude")
                val geofenceRadius = doc.getDouble("geofenceRadius")?.toFloat() ?: 150f
                val triggerDirection = doc.getString("triggerDirection") ?: "ARRIVAL"
                val reminderTone = doc.getString("reminderTone") ?: "DEFAULT"
                val isDeleted = doc.getBoolean("isDeleted") ?: false
                val deletedAt = doc.getLong("deletedAt")

                remoteList.add(
                    Task(
                        id = id,
                        title = title,
                        description = description,
                        priority = priority,
                        dueDate = dueDate,
                        completionStatus = completionStatus,
                        status = completionStatus,
                        isHabit = isHabit,
                        habitFrequency = habitFrequency,
                        isCompleted = isCompleted,
                        category = category,
                        subtasksJson = subtasksJson,
                        locationName = locationName,
                        latitude = latitude,
                        longitude = longitude,
                        geofenceRadius = geofenceRadius,
                        triggerDirection = triggerDirection,
                        reminderTone = reminderTone,
                        isDeleted = isDeleted,
                        deletedAt = deletedAt
                    )
                )
            } catch (_: Exception) {}
        }
        return remoteList
    }

    suspend fun getTasks(userId: String = getUserId()): List<Task> {
        val snapshot = firestore.collection("users").document(userId).collection("userTasks").get().await()
        return snapshot.toObjects(Task::class.java)
    }

    fun listenToUserTasks(
        userId: String = getUserId(),
        onTasksUpdated: (List<Task>) -> Unit
    ): com.google.firebase.firestore.ListenerRegistration {
        return firestore.collection("users")
            .document(userId)
            .collection("userTasks")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val tasks = mutableListOf<Task>()
                for (doc in snapshot.documents) {
                    try {
                        val id = doc.getLong("id")?.toInt() ?: continue
                        val title = doc.getString("title") ?: "Synced Task"
                        val description = doc.getString("description")
                        val priority = doc.getString("priority") ?: "Medium"
                        val dueDate = doc.getLong("dueDate")
                        val completionStatus = doc.getString("completionStatus") ?: "PENDING"
                        val isHabit = doc.getBoolean("isHabit") ?: false
                        val habitFrequency = doc.getString("habitFrequency")
                        val isCompleted = doc.getBoolean("isCompleted") ?: false
                        val category = doc.getString("category")
                        val subtasksJson = doc.getString("subtasksJson")
                        val locationName = doc.getString("locationName")
                        val latitude = doc.getDouble("latitude")
                        val longitude = doc.getDouble("longitude")
                        val geofenceRadius = doc.getDouble("geofenceRadius")?.toFloat() ?: 150f
                        val triggerDirection = doc.getString("triggerDirection") ?: "ARRIVAL"
                        val reminderTone = doc.getString("reminderTone") ?: "DEFAULT"
                        val isDeleted = doc.getBoolean("isDeleted") ?: false
                        val deletedAt = doc.getLong("deletedAt")

                        tasks.add(
                            Task(
                                id = id,
                                title = title,
                                description = description,
                                priority = priority,
                                dueDate = dueDate,
                                completionStatus = completionStatus,
                                status = completionStatus,
                                isHabit = isHabit,
                                habitFrequency = habitFrequency,
                                isCompleted = isCompleted,
                                category = category,
                                subtasksJson = subtasksJson,
                                locationName = locationName,
                                latitude = latitude,
                                longitude = longitude,
                                geofenceRadius = geofenceRadius,
                                triggerDirection = triggerDirection,
                                reminderTone = reminderTone,
                                isDeleted = isDeleted,
                                deletedAt = deletedAt
                            )
                        )
                    } catch (_: Exception) {}
                }
                onTasksUpdated(tasks)
            }
    }
}
