package com.pdv.pos.ia

// Las tres APIs son compatibles con el formato "chat completions" de OpenAI
// (PLAN.md Parte 14), asi que un solo cliente (LlmClient) las cubre a todas
// variando baseUrl/modelo, sin una implementacion por proveedor.
//
// modelosSugeridos es una lista de sugerencias para el combo editable de
// Configuracion (PLAN.md Parte 14, sub-paso 2), no una lista cerrada: el
// campo sigue aceptando cualquier texto, porque los proveedores agregan y
// retiran modelos con frecuencia. modeloPorDefecto es siempre la primera
// sugerencia.
enum class LlmProvider(
    val chatCompletionsUrl: String,
    val modeloPorDefecto: String,
    val modelosSugeridos: List<String>,
) {
    DEEP_SEEK(
        chatCompletionsUrl = "https://api.deepseek.com/chat/completions",
        modeloPorDefecto = "deepseek-chat",
        modelosSugeridos = listOf(
            "deepseek-chat", // uso general, mas economico
            "deepseek-reasoner", // mejor razonamiento, mas caro/lento
        ),
    ),
    OPEN_AI(
        chatCompletionsUrl = "https://api.openai.com/v1/chat/completions",
        modeloPorDefecto = "gpt-4o-mini",
        modelosSugeridos = listOf(
            "gpt-4o-mini", // uso general, mas economico
            "gpt-4o", // mejor razonamiento
            "o4-mini", // razonamiento profundo, mas caro/lento
        ),
    ),

    // OpenRouter agrupa multiples modelos bajo el mismo endpoint con un
    // prefijo "proveedor/modelo".
    OPEN_ROUTER(
        chatCompletionsUrl = "https://openrouter.ai/api/v1/chat/completions",
        modeloPorDefecto = "openai/gpt-4o-mini",
        modelosSugeridos = listOf(
            "openai/gpt-4o-mini", // uso general, mas economico
            "anthropic/claude-3.5-sonnet", // mejor razonamiento
            "meta-llama/llama-3.1-8b-instruct:free", // gratuito
        ),
    ),
}
