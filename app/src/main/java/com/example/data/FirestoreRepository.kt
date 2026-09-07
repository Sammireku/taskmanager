package com.example.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import com.example.data.Task

class FirestoreRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val tasksCollection = firestore.collection("tasks")

    suspend fun addTask(task: Task, userId: String) {
        tasksCollection.document(userId).collection("userTasks").add(task).await()
    }

    suspend fun getTasks(userId: String): List<Task> {
        val snapshot = tasksCollection.document(userId).collection("userTasks").get().await()
        return snapshot.toObjects(Task::class.java)
    }
}
