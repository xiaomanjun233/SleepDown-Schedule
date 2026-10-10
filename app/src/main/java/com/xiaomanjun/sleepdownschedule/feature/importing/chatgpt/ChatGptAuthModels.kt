package com.xiaomanjun.sleepdownschedule.feature.importing.chatgpt

data class ChatGptModel(val slug: String, val displayName: String, val supportsImages: Boolean = false)
data class ChatGptAccount(val id: String, val displayName: String, val email: String?, val isSignedIn: Boolean, val planUsageEnabled: Boolean)
data class ChatGptAuthState(
    val account: ChatGptAccount? = null,
    val accounts: List<ChatGptAccount> = emptyList(),
    val isSigningIn: Boolean = false,
    val errorMessage: String? = null,
    val models: List<ChatGptModel> = emptyList(),
    val isLoadingModels: Boolean = false
)
data class ChatGptSignOutResult(val remoteRevocationConfirmed: Boolean, val message: String)
