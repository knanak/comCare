package com.example.comcare

import android.app.Application
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import com.kakao.sdk.common.KakaoSdk

class MyApplication : Application() {

    companion object {

        // Application 인스턴스 (필요한 경우를 위해)
        lateinit var instance: MyApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()

        instance = this

        // 다크 모드 강제 적용
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)

        // Kakao SDK 초기화
        KakaoSdk.init(this, KAKAO_KEY)

        Log.d("MyApplication", "Application initialized - Kakao SDK ready")

        // 디버그용 키 해시 출력 (개발 시에만 사용)
        if (BuildConfig.DEBUG) {
            val keyHash = com.kakao.sdk.common.util.Utility.getKeyHash(this)
            Log.d("MyApplication", "Kakao Key Hash: $keyHash")
        }
    }
}