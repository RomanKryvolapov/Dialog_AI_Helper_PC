package models.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ModelParametersTest {

    private fun sent(model: LlmModel, chosen: ModelParameters?) = effectiveParameters(model.parameterSupport, chosen)

    private fun model(id: String) = resolveLlmModel(id)!!

    @Test
    fun `with nothing chosen each model gets what the app sent before the settings existed`() {
        assertEquals(ModelParameters(temperature = 0.8, thinking = "minimal"), sent(model("gemini-3.5-flash-lite"), null))
        assertEquals(ModelParameters(temperature = 0.8, thinking = "minimal"), sent(model("gemma-4-31b-it"), null))
        assertEquals(ModelParameters(thinking = "none"), sent(model("gpt-6.1-terra"), null))
        assertEquals(ModelParameters(), sent(model("gpt-audio-1.5"), null))
        assertEquals(ModelParameters(thinking = "between_tools"), sent(model("claude-sonnet-5-5"), null))
    }

    @Test
    fun `GPT-6_1 Terra gets temperature and top P only without reasoning`() {
        val chosen = ModelParameters(temperature = 0.3, topP = 0.9, thinking = "low", verbosity = "low", maxOutputTokens = 400)
        assertEquals(ModelParameters(thinking = "low", verbosity = "low", maxOutputTokens = 400), sent(model("gpt-6.1-terra"), chosen))
        assertEquals(
            ModelParameters(temperature = 0.3, topP = 0.9, thinking = "none", verbosity = "low", maxOutputTokens = 400),
            sent(model("gpt-6.1-terra"), chosen.copy(thinking = null)),
        )
    }

    @Test
    fun `a setting the model refuses is never sent`() {
        val everything = ModelParameters(temperature = 0.5, topP = 0.9, topK = 40, thinking = "high", verbosity = "low")
        assertEquals(ModelParameters(temperature = 0.5), sent(model("gpt-audio-1.5"), everything))
        assertEquals(ModelParameters(thinking = "between_tools"), sent(model("claude-sonnet-5-5"), everything.copy(thinking = null)))
        assertEquals("minimal", sent(model("gemma-4-26b-a4b-it"), everything).thinking, "Gemma 4 refuses any level but minimal")
        assertNull(sent(model("gemini-3.5-flash-lite"), everything).verbosity)
        assertEquals("high", sent(model("gemini-3.5-flash-lite"), everything).thinking)
        assertEquals("adaptive", sent(model("claude-sonnet-5-5"), ModelParameters(thinking = "adaptive")).thinking)
    }
}
