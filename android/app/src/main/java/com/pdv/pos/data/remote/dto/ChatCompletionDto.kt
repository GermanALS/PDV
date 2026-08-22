package com.pdv.pos.data.remote.dto

import kotlinx.serialization.Serializable

// Shape "chat completions" comun a DeepSeek/OpenAI/OpenRouter (PLAN.md
// Parte 14) - las tres APIs son compatibles con el formato de OpenAI.
@Serializable
data class ChatMessageDto(
    val role: String,
    val content: String,
)

@Serializable
data class ChatCompletionRequestDto(
    val model: String,
    val messages: List<ChatMessageDto>,
)

@Serializable
data class ChatCompletionChoiceDto(
    val message: ChatMessageDto,
)

@Serializable
data class ChatCompletionResponseDto(
    val choices: List<ChatCompletionChoiceDto>,
)
