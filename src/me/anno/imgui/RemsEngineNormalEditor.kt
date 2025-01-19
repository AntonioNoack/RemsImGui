package me.anno.imgui

import imgui.ImGui
import imgui.flag.ImGuiCond
import imgui.type.ImBoolean
import me.anno.engine.EngineBase
import me.anno.engine.RemsEngine
import me.anno.gpu.RenderDoc


class RemsEngineNormalEditor : RemsEngineAppBase() {

    override fun createInstance(): EngineBase = RemsEngine()

    override fun process() {
        super.process()

        // your standard ImGui-things
        ImGui.text("Hello World")
        showSecondaryWindow()
    }

    private val showSecondaryWindow = ImBoolean()
    private val sampleBoolean = ImBoolean()

    private fun showSecondaryWindow() {
        ImGui.setNextWindowSize(500f, 400f, ImGuiCond.Once)
        ImGui.setNextWindowPos(ImGui.getMainViewport().posX + 100, ImGui.getMainViewport().posY + 100, ImGuiCond.Once)
        if (ImGui.begin("Secondary Window", showSecondaryWindow)) {
            ImGui.text("This a second window")
            ImGui.checkbox("Sample Boolean", sampleBoolean)
        }
        ImGui.end()
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            // start Rem's Engine using ImGUI
            RenderDoc.loadRenderDoc() // needs to be initialized first
            launch(RemsEngineNormalEditor())
        }
    }
}