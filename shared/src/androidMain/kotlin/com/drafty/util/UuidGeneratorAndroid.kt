package com.drafty.util

actual fun generateUuid(): String = java.util.UUID.randomUUID().toString()
