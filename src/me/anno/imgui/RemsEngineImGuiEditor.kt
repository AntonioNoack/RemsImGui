package me.anno.imgui

import imgui.ImGui
import me.anno.ecs.Entity
import me.anno.ecs.components.mesh.MeshComponent
import me.anno.ecs.prefab.PrefabSaveable
import me.anno.engine.DefaultAssets
import me.anno.engine.EngineBase
import me.anno.engine.ui.render.SceneView.Companion.testScene
import me.anno.gpu.OSWindow
import me.anno.gpu.RenderDoc
import me.anno.ui.debug.TestEngine
import me.anno.utils.types.Floats.toRadians

class RemsEngineImGuiEditor : RemsEngineAppBase() {

    private lateinit var cube: Entity
    private lateinit var floor: Entity

    private fun createScene(): PrefabSaveable {

        val scene = Entity("Scene")
        cube = Entity("Cube", scene)
            .add(MeshComponent(DefaultAssets.flatCube))

        floor = Entity("Floor", scene)
            .add(MeshComponent(DefaultAssets.plane))
            .setPosition(0.0, -1.0, 0.0)
            .setScale(10.0)

        return scene
    }

    override fun createInstance(): EngineBase {
        return TestEngine("RemsEngine - PlayMode") {
            listOf(testScene(createScene()))
        }
    }

    override fun darkenBackground(window: OSWindow) {
        // we don't want darkening -> do nothing
    }

    val cubePosition = FloatArray(3)
    val cubeRotation = FloatArray(3)
    val floorScale = FloatArray(1) { 10f }

    override fun process() {
        super.process()

        // your standard ImGui-things
        ImGui.text("Scene Controls")
        ImGui.dragFloat3("Cube Position", cubePosition, 0.025f)
        cube.position = cube.position.set(cubePosition)
        ImGui.dragFloat3("Cube Rotation", cubeRotation, 1f)
        cube.rotation = cube.rotation.rotationYXZ(
            cubeRotation[1].toDouble().toRadians(),
            cubeRotation[0].toDouble().toRadians(),
            cubeRotation[2].toDouble().toRadians()
        )
        ImGui.dragFloat("Floor Scale", floorScale, 0.01f)
        floor.setScale(floorScale[0].toDouble())
        // todo re-create our tree view using imGui

    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            // start Rem's Engine using ImGUI
            RenderDoc.loadRenderDoc() // needs to be initialized first
            launch(RemsEngineImGuiEditor())
        }
    }
}