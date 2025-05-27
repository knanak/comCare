package com.example.comcare

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.util.Calendar

object RequestCounterHelper {
    private const val TAG = "RequestCounterHelper"
    private const val PREFS_NAME = "ChatPrefs"
    private const val REQUEST_COUNT_KEY = "request_count"
    private const val LAST_REQUEST_DATE_KEY = "last_request_date"
    private const val MAX_REQUESTS_PER_DAY = 10

    private var sharedPrefs: SharedPreferences? = null

    fun init(context: Context) {
        if (sharedPrefs == null) {
            sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    // 오늘 날짜 확인 및 카운트 초기화
    private fun checkAndResetDailyCount() {
        val prefs = sharedPrefs ?: return

        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val lastRequestDate = prefs.getLong(LAST_REQUEST_DATE_KEY, 0)
        val lastRequestCalendar = Calendar.getInstance().apply {
            timeInMillis = lastRequestDate
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        // 날짜가 바뀌었으면 카운트 초기화
        if (today != lastRequestCalendar) {
            prefs.edit().apply {
                putInt(REQUEST_COUNT_KEY, 0)
                putLong(LAST_REQUEST_DATE_KEY, System.currentTimeMillis())
                apply()
            }
            Log.d(TAG, "Daily count reset - new day started")
        }
    }

    // 현재 요청 카운트 가져오기
    fun getCurrentRequestCount(): Int {
        checkAndResetDailyCount()
        return sharedPrefs?.getInt(REQUEST_COUNT_KEY, 0) ?: 0
    }

    // 요청 카운트 증가
    fun incrementRequestCount() {
        checkAndResetDailyCount()
        val prefs = sharedPrefs ?: return
        val currentCount = prefs.getInt(REQUEST_COUNT_KEY, 0)
        prefs.edit().apply {
            putInt(REQUEST_COUNT_KEY, currentCount + 1)
            putLong(LAST_REQUEST_DATE_KEY, System.currentTimeMillis())
            apply()
        }
        Log.d(TAG, "Request count incremented to: ${currentCount + 1}")
    }

    // 채팅 가능 여부 확인
    fun canSendMessage(): Boolean {
        checkAndResetDailyCount()
        val currentCount = getCurrentRequestCount()
        return currentCount < MAX_REQUESTS_PER_DAY
    }

    // 남은 채팅 횟수 가져오기
    fun getRemainingMessages(): Int {
        checkAndResetDailyCount()
        val currentCount = getCurrentRequestCount()
        return (MAX_REQUESTS_PER_DAY - currentCount).coerceAtLeast(0)
    }
}