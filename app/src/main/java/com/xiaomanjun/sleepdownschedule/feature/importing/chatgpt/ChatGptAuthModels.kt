package com.xiaomanjun.sleepdownschedule.feature.importing.chatgpt

data class ChatGptModel(val slug: String, val displayName: String, val supportsImages: Boolean = false)
data class ChatGptAccount(val id: String, val displayName: String, val email: String?, val isSignedIn: Boolean, val planUsageEnabled: Boolean)
enum class ChatGptLoginStage(val label: String) {
    Preparing("正在准备登录…"),
    Authorizing("请在浏览器中完成授权，完成后关闭页面返回。"),
    Exchanging("正在完成登录…"),
    Verifying("正在验证账号…")
}
data class ChatGptAuthState(
    val account: ChatGptAccount? = null,
    val accounts: List<ChatGptAccount> = emptyList(),
    val isSigningIn: Boolean = false,
    val loginStage: ChatGptLoginStage = ChatGptLoginStage.Preparing,
    val errorMessage: String? = null,
    val models: List<ChatGptModel> = emptyList(),
    val isLoadingModels: Boolean = false
)
data class ChatGptSignOutResult(val remoteRevocationConfirmed: Boolean, val message: String)
