package com.example.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

class FirestoreRepository {
    private val firestore: FirebaseFirestore? by lazy {
        try {
            FirebaseFirestore.getInstance()
        } catch (_: Exception) {
            null
        }
    }
    private val auth: FirebaseAuth? by lazy {
        try {
            FirebaseAuth.getInstance()
        } catch (_: Exception) {
            null
        }
    }

    fun getUserId(): String? {
        return try {
            auth?.currentUser?.uid
        } catch (_: Exception) {
            null
        }
    }

    suspend fun saveTask(task: Task, userId: String? = getUserId()) {
        val targetUid = userId ?: getUserId() ?: return
        val db = firestore ?: return
        try {
            val userDoc = db.collection("users").document(targetUid).collection("userTasks").document(task.id.toString())
            val taskMap = hashMapOf<String, Any?>(
                "id" to task.id,
                "title" to task.safeTitle,
                "description" to task.description,
                "priority" to task.safePriority,
                "dueDate" to task.dueDate,
                "status" to task.safeStatus,
                "isHabit" to task.isHabit,
                "habitFrequency" to task.habitFrequency,
                "category" to task.safeCategory,
                "subtasksJson" to task.subtasksJson,
                "locationName" to task.locationName,
                "latitude" to task.latitude,
                "longitude" to task.longitude,
                "geofenceRadius" to task.geofenceRadius,
                "triggerDirection" to task.safeTriggerDirection,
                "reminderTone" to task.safeReminderTone,
                "deletedAt" to task.deletedAt
            )
            userDoc.set(taskMap, SetOptions.merge()).await()
        } catch (e: Exception) {
            // Log or handle offline state
        }
    }

    suspend fun syncTasks(localTasks: List<Task>, userId: String? = getUserId()): List<Task> {
        val targetUid = userId ?: getUserId() ?: return emptyList()
        val db = firestore ?: return emptyList()
        // Push local tasks
        for (t in localTasks) {
            saveTask(t, targetUid)
        }

        // Pull remote tasks from Firestore
        val snapshot = db.collection("users").document(targetUid).collection("userTasks").get().await()
        val remoteList = mutableListOf<Task>()
        for (doc in snapshot.documents) {
            try {
                val id = doc.getLong("id")?.toInt() ?: continue
                val title = doc.getString("title") ?: "Synced Task"
                val description = doc.getString("description")
                val priority = doc.getString("priority") ?: "Medium"
                val dueDate = doc.getLong("dueDate")
                val status = doc.getString("status") ?: doc.getString("completionStatus") ?: "PENDING"
                val isHabit = doc.getBoolean("isHabit") ?: false
                val habitFrequency = doc.getString("habitFrequency")
                val category = doc.getString("category")
                val subtasksJson = doc.getString("subtasksJson")
                val locationName = doc.getString("locationName")
                val latitude = doc.getDouble("latitude")
                val longitude = doc.getDouble("longitude")
                val geofenceRadius = doc.getDouble("geofenceRadius")?.toFloat() ?: 150f
                val triggerDirection = doc.getString("triggerDirection") ?: "ARRIVAL"
                val reminderTone = doc.getString("reminderTone") ?: "DEFAULT"
                val deletedAt = doc.getLong("deletedAt") ?: if (doc.getBoolean("isDeleted") == true) System.currentTimeMillis() else null

                remoteList.add(
                    Task(
                        id = id,
                        title = title,
                        description = description,
                        priority = priority,
                        dueDate = dueDate,
                        status = status,
                        isHabit = isHabit,
                        habitFrequency = habitFrequency,
                        category = category,
                        subtasksJson = subtasksJson,
                        locationName = locationName,
                        latitude = latitude,
                        longitude = longitude,
                        geofenceRadius = geofenceRadius,
                        triggerDirection = triggerDirection,
                        reminderTone = reminderTone,
                        deletedAt = deletedAt
                    )
                )
            } catch (_: Exception) {}
        }
        return remoteList
    }

    suspend fun getTasks(userId: String? = getUserId()): List<Task> {
        val targetUid = userId ?: getUserId() ?: return emptyList()
        val db = firestore ?: return emptyList()
        val snapshot = db.collection("users").document(targetUid).collection("userTasks").get().await()
        return snapshot.toObjects(Task::class.java)
    }

    fun listenToUserTasks(
        userId: String? = getUserId(),
        onTasksUpdated: (List<Task>) -> Unit
    ): com.google.firebase.firestore.ListenerRegistration? {
        val targetUid = userId ?: getUserId() ?: return null
        val db = firestore ?: return null
        return db.collection("users")
            .document(targetUid)
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
                        val status = doc.getString("status") ?: doc.getString("completionStatus") ?: "PENDING"
                        val isHabit = doc.getBoolean("isHabit") ?: false
                        val habitFrequency = doc.getString("habitFrequency")
                        val category = doc.getString("category")
                        val subtasksJson = doc.getString("subtasksJson")
                        val locationName = doc.getString("locationName")
                        val latitude = doc.getDouble("latitude")
                        val longitude = doc.getDouble("longitude")
                        val geofenceRadius = doc.getDouble("geofenceRadius")?.toFloat() ?: 150f
                        val triggerDirection = doc.getString("triggerDirection") ?: "ARRIVAL"
                        val reminderTone = doc.getString("reminderTone") ?: "DEFAULT"
                        val deletedAt = doc.getLong("deletedAt") ?: if (doc.getBoolean("isDeleted") == true) System.currentTimeMillis() else null

                        tasks.add(
                            Task(
                                id = id,
                                title = title,
                                description = description,
                                priority = priority,
                                dueDate = dueDate,
                                status = status,
                                isHabit = isHabit,
                                habitFrequency = habitFrequency,
                                category = category,
                                subtasksJson = subtasksJson,
                                locationName = locationName,
                                latitude = latitude,
                                longitude = longitude,
                                geofenceRadius = geofenceRadius,
                                triggerDirection = triggerDirection,
                                reminderTone = reminderTone,
                                deletedAt = deletedAt
                            )
                        )
                    } catch (_: Exception) {}
                }
                onTasksUpdated(tasks)
            }
    }
}
