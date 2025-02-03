package me.anno.imgui

import imgui.ImFontConfig
import imgui.ImGui
import imgui.app.Application
import imgui.flag.ImGuiFocusedFlags
import me.anno.Time
import me.anno.engine.EngineBase
import me.anno.engine.OfficialExtensions
import me.anno.extensions.ExtensionLoader
import me.anno.gpu.GFX
import me.anno.gpu.OSWindow
import me.anno.gpu.RenderStep
import me.anno.gpu.WindowManagement
import me.anno.gpu.drawing.DrawRectangles
import me.anno.input.GLFWListeners.handleCharMods
import me.anno.input.GLFWListeners.handleCursorPos
import me.anno.input.GLFWListeners.handleDropCallback
import me.anno.input.GLFWListeners.handleKeyCallback
import me.anno.input.GLFWListeners.handleMouseButton
import me.anno.input.GLFWListeners.handleScroll
import me.anno.io.files.FileReference
import me.anno.utils.Clock
import me.anno.utils.Color.black
import me.anno.utils.Color.withAlpha
import org.lwjgl.glfw.GLFW.*

abstract class RemsEngineAppBase : Application() {

    abstract fun createInstance(): EngineBase

    override fun preRun() {

        val window = GFX.someWindow
        GFX.activeWindow = window
        GFX.windows.remove(window)
        GFX.windows.add(window)
        window.pointer = handle

        val clock = Clock("RemsImGui")
        WindowManagement.prepareForRendering(clock)
        GFX.check()
        GFX.setupBasics(clock)

        val instance = createInstance()
        EngineBase.instance = instance
        instance.setupNames()
        instance.loadConfig()

        OfficialExtensions.register()
        ExtensionLoader.load()

        // needs extensions to load image
        WindowManagement.setIcon(handle)
        instance.gameInit()

        overrideGLFWListeners(window)
    }

    private fun overrideGLFWListeners(window: OSWindow) {
        val handle = handle
        val prevDrop = glfwSetDropCallback(handle, null)
        glfwSetDropCallback(window.pointer) { handle1: Long, count: Int, names: Long ->
            prevDrop?.invoke(handle1, count, names)
            if (isInFocus) handleDropCallback(window, count, names)
        }
        val prevCharMods = glfwSetCharModsCallback(handle, null)
        glfwSetCharModsCallback(window.pointer) { handle1, codepoint, mods ->
            prevCharMods?.invoke(handle1, codepoint, mods)
            if (isInFocus) handleCharMods(window, codepoint, mods)
        }
        val prevCursorPos = glfwSetCursorPosCallback(handle, null)
        glfwSetCursorPosCallback(window.pointer) { handle1, xPosition, yPosition ->
            prevCursorPos?.invoke(handle1, xPosition, yPosition)
            handleCursorPos(window, xPosition, yPosition)
        }
        val prevMouseButton = glfwSetMouseButtonCallback(handle, null)
        glfwSetMouseButtonCallback(handle) { window1, button, action, mods ->
            // todo bug: if clicking on ImGui window, we must not trigger the click on the window
            // todo bug: F11/fullscreen crashes ImGui
            prevMouseButton?.invoke(window1, button, action, mods)
            if (isInFocus || action == GLFW_RELEASE) handleMouseButton(window, button, action, mods)
        }
        val prevScroll = glfwSetScrollCallback(handle, null)
        glfwSetScrollCallback(window.pointer) { handle1, xOffset, yOffset ->
            prevScroll?.invoke(handle1, xOffset, yOffset)
            if (isInFocus) handleScroll(window, xOffset, yOffset)
        }
        val prevKey = glfwSetKeyCallback(handle, null)
        glfwSetKeyCallback(window.pointer) { window1, key, scancode, action, mods ->
            prevKey?.invoke(window1, key, scancode, action, mods)
            if (isInFocus || action == GLFW_RELEASE) handleKeyCallback(window, window1, key, scancode, action, mods)
        }
    }

    private val isInFocus get() = !ImGui.isWindowFocused(ImGuiFocusedFlags.AnyWindow)

    private val xs = IntArray(1)
    private val ys = IntArray(1)
    private fun renderRemsEngine() {

        // update time
        Time.updateTime()

        // update window size
        val window = GFX.someWindow

        glfwGetWindowSize(handle, xs, ys)
        window.width = xs[0]
        window.height = ys[0]

        window.isInFocus = isInFocus
        window.framesSinceLastInteraction = 0

        GFX.activeWindow = window
        window.needsRefresh = true
        RenderStep.renderStep(window, true)

        if (!isInFocus) {
            darkenBackground(window)
        }
    }

    open fun darkenBackground(window: OSWindow) {
        // draw shadow over scene
        val color = black.withAlpha(0.5f)
        DrawRectangles.drawRect(0, 0, window.width, window.height, color)
    }

    fun loadFont(fontPath: FileReference, fontSize: Float) {
        fontPath.readBytes { bytes, err ->
            if (bytes == null) {
                err?.printStackTrace()
                return@readBytes
            }
            val io = ImGui.getIO()
            val fontAtlas = io.fonts
            val fontConfig = ImFontConfig()
            fontAtlas.addFontFromMemoryTTF(bytes, fontSize, fontConfig)
            fontConfig.destroy()
            ImGui.getIO().fonts.build()
        }
    }

    override fun process() {
        renderRemsEngine()
    }
}