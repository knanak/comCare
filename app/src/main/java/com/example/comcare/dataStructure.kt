package com.example.comcare


data class Place(
    val id: String,
    val name: String,
    val facilityCode: String,
    val facilityKind: String,
    val facilityKindDetail: String,
    val district: String,
    val address: String,
    val tel: String,
    val zipCode: String,
    val service1: List<String>,
    val service2: List<String>,
    val rating: String = "",
    val rating_year: String = "",
    val full: String = "0",
    val now: String = "0",
    val wating: String = "0",
    val bus: String = ""
)