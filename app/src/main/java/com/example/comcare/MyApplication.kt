// 1. MyApplication.kt (MyApplication.kt 파일명 변경)
package com.example.comcare

import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.kakao.sdk.common.KakaoSdk
// 4. MainActivity.kt에 필요한 import 추가
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.common.model.ClientError
import com.kakao.sdk.common.model.ClientErrorCause
import com.kakao.sdk.user.UserApiClient
import kotlinx.coroutines.launch

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.kakao.sdk.common.util.Utility

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Kakao SDK 초기화
        KakaoSdk.init(this, KAKAO_KEY) // 실제 네이티브 앱 키로 변경
//
//        val keyHash = Utility.getKeyHash(this)
//        Log.d("KakaoKeyHash", "키 해시: $keyHash")

    }
}


//
//// 2. LoginScreen.kt 수정 - 카카오 로그인 기능 추가
//@OptIn(ExperimentalMaterial3Api::class)
//@Composable
//fun LoginScreen(
//    navController: NavController,
//    supabaseHelper: SupabaseDatabaseHelper,
//    onLoginSuccess: (String) -> Unit
//) {
//    var userId by rememberSaveable { mutableStateOf("") }
//    var password by rememberSaveable { mutableStateOf("") }
//    var passwordVisible by rememberSaveable { mutableStateOf(false) }
//    var showError by remember { mutableStateOf(false) }
//    var errorMessage by remember { mutableStateOf("") }
//    var isLoading by remember { mutableStateOf(false) }
//
//    val context = LocalContext.current
//    val coroutineScope = rememberCoroutineScope()
//
//    Column(
//        modifier = Modifier
//            .fillMaxSize()
//            .padding(16.dp),
//        horizontalAlignment = Alignment.CenterHorizontally,
//        verticalArrangement = Arrangement.Center
//    ) {
//        // 앱 로고 또는 타이틀
//        Card(
//            modifier = Modifier
//                .fillMaxWidth()
//                .padding(bottom = 32.dp),
//            colors = CardDefaults.cardColors(
//                containerColor = Color(0xFFc6f584)
//            )
//        ) {
//            Text(
//                text = "오비서",
//                style = MaterialTheme.typography.displayMedium,
//                fontWeight = FontWeight.Bold,
//                color = Color.Black,
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .padding(vertical = 24.dp),
//                textAlign = TextAlign.Center
//            )
//        }
//
//        // 로그인 폼
//        Card(
//            modifier = Modifier.fillMaxWidth(),
//            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
//        ) {
//            Column(
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .padding(24.dp),
//                horizontalAlignment = Alignment.CenterHorizontally
//            ) {
//                Text(
//                    text = "로그인",
//                    style = MaterialTheme.typography.headlineMedium,
//                    fontWeight = FontWeight.Bold,
//                    modifier = Modifier.padding(bottom = 24.dp)
//                )
//
//                // 아이디 입력
//                OutlinedTextField(
//                    value = userId,
//                    onValueChange = { userId = it },
//                    label = { Text("아이디") },
//                    modifier = Modifier.fillMaxWidth(),
//                    singleLine = true,
//                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
//                    textStyle = MaterialTheme.typography.bodyLarge
//                )
//
//                Spacer(modifier = Modifier.height(16.dp))
//
//                // 비밀번호 입력
//                OutlinedTextField(
//                    value = password,
//                    onValueChange = { password = it },
//                    label = { Text("비밀번호") },
//                    modifier = Modifier.fillMaxWidth(),
//                    singleLine = true,
//                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
//                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
//                    trailingIcon = {
//                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
//                            Icon(
//                                imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
//                                contentDescription = if (passwordVisible) "비밀번호 숨기기" else "비밀번호 보기"
//                            )
//                        }
//                    },
//                    textStyle = MaterialTheme.typography.bodyLarge
//                )
//
//                // 에러 메시지
//                if (showError) {
//                    Spacer(modifier = Modifier.height(8.dp))
//                    Text(
//                        text = errorMessage,
//                        color = MaterialTheme.colorScheme.error,
//                        style = MaterialTheme.typography.bodyMedium
//                    )
//                }
//
//                Spacer(modifier = Modifier.height(24.dp))
//
//                // 로그인 버튼
//                Button(
//                    onClick = {
//                        if (userId.isNotEmpty() && password.isNotEmpty()) {
//                            isLoading = true
//                            showError = false
//
//                            coroutineScope.launch {
//                                val loginSuccess = supabaseHelper.loginUser(userId, password)
//                                isLoading = false
//
//                                if (loginSuccess) {
//                                    onLoginSuccess(userId)
//                                } else {
//                                    showError = true
//                                    errorMessage = "아이디 또는 비밀번호가 올바르지 않습니다."
//                                }
//                            }
//                        } else {
//                            showError = true
//                            errorMessage = "아이디와 비밀번호를 입력해주세요."
//                        }
//                    },
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .height(56.dp),
//                    colors = ButtonDefaults.buttonColors(
//                        containerColor = Color(0xFFc6f584),
//                        contentColor = Color.Black
//                    ),
//                    enabled = !isLoading
//                ) {
//                    if (isLoading) {
//                        CircularProgressIndicator(
//                            modifier = Modifier.size(24.dp),
//                            color = Color.Black
//                        )
//                    } else {
//                        Text(
//                            "로그인",
//                            style = MaterialTheme.typography.titleMedium,
//                            fontWeight = FontWeight.Bold
//                        )
//                    }
//                }
//
//                Spacer(modifier = Modifier.height(16.dp))
//
//                // 구분선
//                Row(
//                    modifier = Modifier.fillMaxWidth(),
//                    verticalAlignment = Alignment.CenterVertically
//                ) {
//                    Divider(modifier = Modifier.weight(1f))
//                    Text(
//                        "또는",
//                        modifier = Modifier.padding(horizontal = 16.dp),
//                        style = MaterialTheme.typography.bodyMedium,
//                        color = Color.Gray
//                    )
//                    Divider(modifier = Modifier.weight(1f))
//                }
//
//                Spacer(modifier = Modifier.height(16.dp))
//
//                // 카카오 로그인 버튼
//                KakaoLoginButton(
//                    onLoginSuccess = onLoginSuccess,
//                    supabaseHelper = supabaseHelper
//                )
//
//                Spacer(modifier = Modifier.height(16.dp))
//
//                // 회원가입 버튼
//                OutlinedButton(
//                    onClick = {
//                        navController.navigate("signup")
//                    },
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .height(56.dp),
//                    border = BorderStroke(2.dp, Color(0xFFc6f584))
//                ) {
//                    Text(
//                        "회원가입",
//                        style = MaterialTheme.typography.titleMedium,
//                        fontWeight = FontWeight.Bold,
//                        color = Color.Black
//                    )
//                }
//            }
//        }
//    }
//}
//
//// 3. 카카오 로그인 버튼 컴포저블
//@Composable
//fun KakaoLoginButton(
//    onLoginSuccess: (String) -> Unit,
//    supabaseHelper: SupabaseDatabaseHelper
//) {
//    val context = LocalContext.current
//    val coroutineScope = rememberCoroutineScope()
//    var isLoading by remember { mutableStateOf(false) }
//
//    // 카카오 로그인 콜백
//    val callback: (OAuthToken?, Throwable?) -> Unit = { token, error ->
//        if (error != null) {
//            Log.e("KakaoLogin", "카카오 로그인 실패", error)
//            Toast.makeText(context, "카카오 로그인 실패: ${error.message}", Toast.LENGTH_SHORT).show()
//            isLoading = false
//        } else if (token != null) {
//            Log.i("KakaoLogin", "카카오 로그인 성공 ${token.accessToken}")
//
//            // 사용자 정보 가져오기
//            UserApiClient.instance.me { user, error ->
//                if (error != null) {
//                    Log.e("KakaoLogin", "사용자 정보 요청 실패", error)
//                    Toast.makeText(context, "사용자 정보 요청 실패", Toast.LENGTH_SHORT).show()
//                    isLoading = false
//                } else if (user != null) {
//                    val kakaoId = user.id.toString()
//                    val email = user.kakaoAccount?.email ?: ""
//                    val nickname = user.kakaoAccount?.profile?.nickname ?: ""
//                    val profileImage = user.kakaoAccount?.profile?.thumbnailImageUrl ?: ""
//
//                    // Supabase에 카카오 사용자 정보 저장 또는 업데이트
//                    coroutineScope.launch {
//                        val loginSuccess = supabaseHelper.loginOrCreateKakaoUser(
//                            kakaoId = kakaoId,
//                            email = email,
//                            nickname = nickname,
//                            profileImage = profileImage
//                        )
//
//                        isLoading = false
//
//                        if (loginSuccess) {
//                            onLoginSuccess("kakao_$kakaoId")
//                        } else {
//                            Toast.makeText(context, "로그인 처리 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
//                        }
//                    }
//                }
//            }
//        }
//    }
//
//    Button(
//        onClick = {
//            isLoading = true
//
//            // 카카오톡 설치 여부 확인
//            if (UserApiClient.instance.isKakaoTalkLoginAvailable(context)) {
//                // 카카오톡으로 로그인
//                UserApiClient.instance.loginWithKakaoTalk(context) { token, error ->
//                    if (error != null) {
//                        Log.e("KakaoLogin", "카카오톡으로 로그인 실패", error)
//
//                        // 사용자가 카카오톡 설치 후 디바이스 권한 요청 화면에서 로그인을 취소한 경우,
//                        // 의도적인 로그인 취소로 보고 카카오계정으로 로그인 시도 없이 로그인 취소로 처리
//                        if (error is ClientError && error.reason == ClientErrorCause.Cancelled) {
//                            isLoading = false
//                            return@loginWithKakaoTalk
//                        }
//
//                        // 카카오톡에 연결된 카카오계정이 없는 경우, 카카오계정으로 로그인 시도
//                        UserApiClient.instance.loginWithKakaoAccount(context, callback = callback)
//                    } else if (token != null) {
//                        Log.i("KakaoLogin", "카카오톡으로 로그인 성공 ${token.accessToken}")
//                        callback(token, null)
//                    }
//                }
//            } else {
//                // 카카오톡이 설치되어 있지 않으면 카카오계정으로 로그인
//                UserApiClient.instance.loginWithKakaoAccount(context, callback = callback)
//            }
//        },
//        modifier = Modifier
//            .fillMaxWidth()
//            .height(56.dp),
//        colors = ButtonDefaults.buttonColors(
//            containerColor = Color(0xFFFEE500), // 카카오 브랜드 컬러
//            contentColor = Color.Black
//        ),
//        enabled = !isLoading
//    ) {
//        if (isLoading) {
//            CircularProgressIndicator(
//                modifier = Modifier.size(24.dp),
//                color = Color.Black
//            )
//        } else {
//            Row(
//                verticalAlignment = Alignment.CenterVertically,
//                horizontalArrangement = Arrangement.Center
//            ) {
//                // 카카오 로고 아이콘 (실제로는 이미지 리소스를 사용해야 함)
//                Icon(
//                    imageVector = Icons.Default.Person,
//                    contentDescription = "Kakao",
//                    modifier = Modifier.size(24.dp)
//                )
//                Spacer(modifier = Modifier.width(8.dp))
//                Text(
//                    "카카오 로그인",
//                    style = MaterialTheme.typography.titleMedium,
//                    fontWeight = FontWeight.Bold
//                )
//            }
//        }
//    }
//}


//@OptIn(ExperimentalMaterial3Api::class)
//@Composable
//fun SignUpScreen(
//    navController: NavController,
//    supabaseHelper: SupabaseDatabaseHelper
//) {
//    var userId by rememberSaveable { mutableStateOf("") }
//    var password by rememberSaveable { mutableStateOf("") }
//    var confirmPassword by rememberSaveable { mutableStateOf("") }
//    var email by rememberSaveable { mutableStateOf("") }
//    var name by rememberSaveable { mutableStateOf("") }
//    var passwordVisible by rememberSaveable { mutableStateOf(false) }
//    var confirmPasswordVisible by rememberSaveable { mutableStateOf(false) }
//    var showError by remember { mutableStateOf(false) }
//    var errorMessage by remember { mutableStateOf("") }
//    var isLoading by remember { mutableStateOf(false) }
//
//    val coroutineScope = rememberCoroutineScope()
//
//    Column(
//        modifier = Modifier
//            .fillMaxSize()
//            .padding(16.dp),
//        horizontalAlignment = Alignment.CenterHorizontally
//    ) {
//        // 상단 바
//        Row(
//            modifier = Modifier
//                .fillMaxWidth()
//                .padding(bottom = 32.dp),
//            verticalAlignment = Alignment.CenterVertically
//        ) {
//            IconButton(onClick = { navController.navigateUp() }) {
//                Icon(
//                    imageVector = Icons.Default.ArrowBack,
//                    contentDescription = "뒤로가기"
//                )
//            }
//            Spacer(modifier = Modifier.width(8.dp))
//            Text(
//                text = "회원가입",
//                style = MaterialTheme.typography.headlineMedium,
//                fontWeight = FontWeight.Bold
//            )
//        }
//
//        // 회원가입 폼
//        Card(
//            modifier = Modifier.fillMaxWidth(),
//            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
//        ) {
//            Column(
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .padding(24.dp),
//                horizontalAlignment = Alignment.CenterHorizontally
//            ) {
//                // 이름 입력
//                OutlinedTextField(
//                    value = name,
//                    onValueChange = { name = it },
//                    label = { Text("이름") },
//                    modifier = Modifier.fillMaxWidth(),
//                    singleLine = true,
//                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
//                    textStyle = MaterialTheme.typography.bodyLarge
//                )
//
//                Spacer(modifier = Modifier.height(16.dp))
//
//                // 아이디 입력
//                OutlinedTextField(
//                    value = userId,
//                    onValueChange = { userId = it },
//                    label = { Text("아이디") },
//                    modifier = Modifier.fillMaxWidth(),
//                    singleLine = true,
//                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
//                    textStyle = MaterialTheme.typography.bodyLarge
//                )
//
//                Spacer(modifier = Modifier.height(16.dp))
//
//                // 이메일 입력
//                OutlinedTextField(
//                    value = email,
//                    onValueChange = { email = it },
//                    label = { Text("이메일") },
//                    modifier = Modifier.fillMaxWidth(),
//                    singleLine = true,
//                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
//                    textStyle = MaterialTheme.typography.bodyLarge
//                )
//
//                Spacer(modifier = Modifier.height(16.dp))
//
//                // 비밀번호 입력
//                OutlinedTextField(
//                    value = password,
//                    onValueChange = { password = it },
//                    label = { Text("비밀번호") },
//                    modifier = Modifier.fillMaxWidth(),
//                    singleLine = true,
//                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
//                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
//                    trailingIcon = {
//                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
//                            Icon(
//                                imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
//                                contentDescription = if (passwordVisible) "비밀번호 숨기기" else "비밀번호 보기"
//                            )
//                        }
//                    },
//                    textStyle = MaterialTheme.typography.bodyLarge
//                )
//
//                Spacer(modifier = Modifier.height(16.dp))
//
//                // 비밀번호 확인 입력
//                OutlinedTextField(
//                    value = confirmPassword,
//                    onValueChange = { confirmPassword = it },
//                    label = { Text("비밀번호 확인") },
//                    modifier = Modifier.fillMaxWidth(),
//                    singleLine = true,
//                    visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
//                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
//                    trailingIcon = {
//                        IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
//                            Icon(
//                                imageVector = if (confirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
//                                contentDescription = if (confirmPasswordVisible) "비밀번호 숨기기" else "비밀번호 보기"
//                            )
//                        }
//                    },
//                    textStyle = MaterialTheme.typography.bodyLarge
//                )
//
//                // 에러 메시지
//                if (showError) {
//                    Spacer(modifier = Modifier.height(8.dp))
//                    Text(
//                        text = errorMessage,
//                        color = MaterialTheme.colorScheme.error,
//                        style = MaterialTheme.typography.bodyMedium
//                    )
//                }
//
//                Spacer(modifier = Modifier.height(24.dp))
//
//                // 회원가입 버튼
//                Button(
//                    onClick = {
//                        when {
//                            name.isEmpty() -> {
//                                showError = true
//                                errorMessage = "이름을 입력해주세요."
//                            }
//                            userId.isEmpty() -> {
//                                showError = true
//                                errorMessage = "아이디를 입력해주세요."
//                            }
//                            email.isEmpty() -> {
//                                showError = true
//                                errorMessage = "이메일을 입력해주세요."
//                            }
//                            password.isEmpty() -> {
//                                showError = true
//                                errorMessage = "비밀번호를 입력해주세요."
//                            }
//                            password.length < 6 -> {
//                                showError = true
//                                errorMessage = "비밀번호는 최소 6자 이상이어야 합니다."
//                            }
//                            password != confirmPassword -> {
//                                showError = true
//                                errorMessage = "비밀번호가 일치하지 않습니다."
//                            }
//                            else -> {
//                                isLoading = true
//                                showError = false
//
//                                coroutineScope.launch {
//                                    val result = supabaseHelper.createUser(
//                                        userId = userId,
//                                        password = password,
//                                        email = email,
//                                        name = name
//                                    )
//
//                                    isLoading = false
//
//                                    when (result) {
//                                        is SupabaseDatabaseHelper.SignUpResult.Success -> {
//                                            // 회원가입 성공 - 로그인 화면으로 이동
//                                            navController.navigate("login") {
//                                                popUpTo("signup") { inclusive = true }
//                                            }
//                                        }
//                                        is SupabaseDatabaseHelper.SignUpResult.UserExists -> {
//                                            showError = true
//                                            errorMessage = "이미 존재하는 아이디입니다."
//                                        }
//                                        is SupabaseDatabaseHelper.SignUpResult.Error -> {
//                                            showError = true
//                                            errorMessage = result.message
//                                        }
//                                    }
//                                }
//                            }
//                        }
//                    },
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .height(56.dp),
//                    colors = ButtonDefaults.buttonColors(
//                        containerColor = Color(0xFFc6f584),
//                        contentColor = Color.Black
//                    ),
//                    enabled = !isLoading
//                ) {
//                    if (isLoading) {
//                        CircularProgressIndicator(
//                            modifier = Modifier.size(24.dp),
//                            color = Color.Black
//                        )
//                    } else {
//                        Text(
//                            "회원가입",
//                            style = MaterialTheme.typography.titleMedium,
//                            fontWeight = FontWeight.Bold
//                        )
//                    }
//                }
//            }
//        }
//    }
//}

