package com.example.coolconnect

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "technicians")
data class TechnicianEntity(
    @PrimaryKey val uid: String,
    val name: String,
    val email: String,
    val phone: String,
    val skill: String,
    val isOnline: Boolean
)

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey val id: String,
    val name: String,
    val price: Double,
    val stock: Int
)

@Entity(tableName = "completed_jobs")
data class CompletedJobEntity(
    @PrimaryKey val uid: String,
    val customerName: String,
    val assignedTechnician: String,
    val totalAmount: Double,
    val completedAt: Long
)
