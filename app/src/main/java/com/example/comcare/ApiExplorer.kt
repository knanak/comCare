package com.example.comcare

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

val secretKey= "554f696d556b6b6d313039646a557a4b"

class ApiExplorer {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            try {
                val urlBuilder = StringBuilder("http://openapi.seoul.go.kr:8088/sample/xml/fcltOpenInfo_OWI/1/5/") /*URL*/
                urlBuilder.append("/" + URLEncoder.encode("sample", "UTF-8")) /*인증키 (sample사용시에는 호출시 제한됩니다.)*/
                urlBuilder.append("/" + URLEncoder.encode("xml", "UTF-8")) /*요청파일타입 (xml,xmlf,xls,json) */
                urlBuilder.append("/" + URLEncoder.encode("CardSubwayStatsNew", "UTF-8")) /*서비스명 (대소문자 구분 필수입니다.)*/
                urlBuilder.append("/" + URLEncoder.encode("1", "UTF-8")) /*요청시작위치 (sample인증키 사용시 5이내 숫자)*/
                urlBuilder.append("/" + URLEncoder.encode("5", "UTF-8")) /*요청종료위치(sample인증키 사용시 5이상 숫자 선택 안 됨)*/
                // 상위 5개는 필수적으로 순서바꾸지 않고 호출해야 합니다.

                // 서비스별 추가 요청 인자이며 자세한 내용은 각 서비스별 '요청인자'부분에 자세히 나와 있습니다.
                urlBuilder.append("/" + URLEncoder.encode("20220301", "UTF-8")) /* 서비스별 추가 요청인자들*/

                val url = URL(urlBuilder.toString())
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Content-type", "application/xml")
                println("Response code: " + conn.responseCode) /* 연결 자체에 대한 확인이 필요하므로 추가합니다.*/

                val rd = if (conn.responseCode in 200..300) {
                    BufferedReader(InputStreamReader(conn.inputStream))
                } else {
                    BufferedReader(InputStreamReader(conn.errorStream))
                }

                val sb = StringBuilder()
                var line: String?
                while (rd.readLine().also { line = it } != null) {
                    sb.append(line)
                }
                rd.close()
                conn.disconnect()
                println(sb.toString())
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }
}