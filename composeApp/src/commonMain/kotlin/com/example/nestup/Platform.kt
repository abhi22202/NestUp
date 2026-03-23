package com.example.nestup

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform