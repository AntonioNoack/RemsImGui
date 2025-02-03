package me.anno.imgui

import imgui.ImGui
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiTreeNodeFlags
import imgui.type.*
import me.anno.config.DefaultConfig
import me.anno.ecs.Entity
import me.anno.ecs.annotations.ExtendableEnum
import me.anno.ecs.components.mesh.MeshComponent
import me.anno.ecs.prefab.PrefabSaveable
import me.anno.engine.DefaultAssets
import me.anno.engine.EngineBase
import me.anno.engine.inspector.CachedProperty
import me.anno.engine.inspector.Inspectable
import me.anno.engine.ui.EditorState
import me.anno.engine.ui.render.RenderView
import me.anno.engine.ui.render.SceneView
import me.anno.engine.ui.render.SceneView.Companion.testScene
import me.anno.gpu.OSWindow
import me.anno.gpu.RenderDoc
import me.anno.io.files.Reference.getReference
import me.anno.io.saveable.Saveable
import me.anno.ui.debug.TestEngine
import me.anno.ui.input.EnumInput
import me.anno.utils.OS.res
import me.anno.utils.types.AnyToBool
import me.anno.utils.types.AnyToDouble
import me.anno.utils.types.AnyToFloat
import me.anno.utils.types.AnyToInt
import me.anno.utils.types.Booleans.toInt
import me.anno.utils.types.Floats.toDegrees
import me.anno.utils.types.Floats.toRadians
import me.anno.utils.types.Strings.camelCaseToTitle
import org.joml.*

class RemsEngineImGuiEditor : RemsEngineAppBase() {

    private lateinit var scene: Entity
    private lateinit var cube: Entity
    private lateinit var floor: Entity

    override fun preRun() {
        super.preRun()
        val defaultFontSize = DefaultConfig.style.getSize("fontSize", 12)
        loadFont(res.getChild("Roboto-Regular.ttf"), defaultFontSize.toFloat())
    }

    private fun createScene(): PrefabSaveable {

        scene = Entity("Scene")
        cube = Entity("Cube", scene)
            .add(MeshComponent(DefaultAssets.flatCube))

        floor = Entity("Floor", scene)
            .add(MeshComponent(DefaultAssets.plane))
            .setPosition(0.0, -1.0, 0.0)
            .setScale(10.0)

        EditorState.select(scene)

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

    private val showTreeView = ImBoolean()

    override fun process() {
        super.process()

        ImGui.setNextWindowSize(500f, 400f, ImGuiCond.Once)
        ImGui.setNextWindowPos(ImGui.getMainViewport().posX + 100, ImGui.getMainViewport().posY + 100, ImGuiCond.Once)
        if (ImGui.begin("Tree View", showTreeView)) {
            showTreeView()
        }
        ImGui.end()

        showPropertyEditor()

        // todo show file explorer

    }

    private fun showTreeView() {
        ImGui.begin("Tree View")
        showTreeView(scene)
        if (ImGui.button("Scene Settings")) {
            val sv = RenderView.currentInstance?.parent as? SceneView
            EditorState.select(sv?.editControls?.settings)
        }
        ImGui.end()
    }

    private fun hasChildren(node: PrefabSaveable): Boolean {
        for (type in node.listChildTypes()) {
            for (child in node.getChildListByType(type)) {
                return true
            }
        }
        return false
    }

    private fun showTreeView(node: PrefabSaveable) {

        val hasChildren = hasChildren(node)
        val flags = ImGuiTreeNodeFlags.OpenOnArrow or
                (!hasChildren).toInt(ImGuiTreeNodeFlags.Leaf or ImGuiTreeNodeFlags.NoTreePushOnOpen)

        val isOpen = ImGui.treeNodeEx("${node.name} (${node.className})", flags)
        if (ImGui.isItemClicked()) {
            EditorState.select(node, ImGui.getIO().keyShift)
        }

        if (isOpen && hasChildren) {
            for (type in node.listChildTypes()) {
                for (child in node.getChildListByType(type)) {
                    showTreeView(child)
                }
            }
            ImGui.treePop()
        }
    }


    private fun showPropertyEditor() {
        val instance = EditorState.selection.firstOrNull()
        if (instance != null) {
            showPropertyEditor(instance)
        } else ImGui.text("Nothing selected")
    }

    private fun showPropertyEditor(instance: Inspectable) {

        val reflections = Saveable.getReflections(instance)
        for ((clazz, properties0) in reflections.propertiesByClass) {
            val properties = properties0.filter { it.serialize && !it.hideInInspector(instance) }
            if (properties.isEmpty()) continue
            val title = (clazz.simpleName ?: "?").camelCaseToTitle()
            val flags = ImGuiTreeNodeFlags.OpenOnArrow or ImGuiTreeNodeFlags.DefaultOpen
            if (!ImGui.treeNodeEx(title, flags)) continue
            val byGroup = properties.groupBy { it.group }
            for ((group, properties1) in byGroup) {
                val needsPop = group != null && ImGui.treeNode(group)
                if (group == null || needsPop) {
                    for (property in properties1.sortedBy { it.order }) {
                        editProperty(instance, property)
                    }
                }
                if (needsPop) ImGui.treePop()
            }
            ImGui.treePop()
        }

        // debug actions
        val flags = ImGuiTreeNodeFlags.OpenOnArrow or ImGuiTreeNodeFlags.DefaultOpen
        if (reflections.debugActions.isNotEmpty() && ImGui.treeNodeEx("Debug Actions", flags)) {
            for (action in reflections.debugActions) {
                if (ImGui.button(action.name.camelCaseToTitle())) {
                    action.call(instance)
                }
            }
            ImGui.treePop()
        }
    }

    private fun editProperty(instance: Inspectable, property: CachedProperty) {
        val name = property.name.camelCaseToTitle()
        val value0 = property[instance]
        when (val type = property.valueClass.simpleName) {
            // todo implement all property types
            "boolean" -> {
                val value = AnyToBool.anyToBool(value0)
                val imBool = imBools.getOrPut(name) { ImBoolean(value) }
                if (ImGui.checkbox(name, imBool)) {
                    property[instance] = imBool.get()
                }
            }
            "int" -> {
                val value = AnyToInt.getInt(value0)
                val imInt = imInts.getOrPut(name) { ImInt(value) }
                if (ImGui.inputInt(name, imInt)) {
                    property[instance] = imInt.get()
                }
            }
            "long" -> {
                // todo long input...
                val value = AnyToInt.getInt(value0)
                val imInt = imInts.getOrPut(name) { ImInt(value) }
                if (ImGui.inputInt(name, imInt)) {
                    property[instance] = imInt.get().toLong()
                }
            }
            "float" -> {
                val value = AnyToFloat.getFloat(value0)
                val imFloat = imFloats.getOrPut(name) { ImFloat(value) }
                if (ImGui.inputFloat(name, imFloat)) {
                    property[instance] = imFloat.get()
                }
            }
            "double" -> {
                val value = AnyToDouble.getDouble(value0)
                val imDouble = imDoubles.getOrPut(name) { ImDouble(value) }
                if (ImGui.inputDouble(name, imDouble)) {
                    property[instance] = imDouble.get()
                }
            }
            "String" -> {
                // todo this isn't working yet, why???
                val value = value0.toString()
                val imString = imStrings.getOrPut(name) { ImString(value) }
                if (ImGui.inputText(name, imString)) {
                    property[instance] = imString.get()
                }
            }
            "Vector2f", "Vector2d" -> {
                val value = value0 as Vector
                val values = FloatArray(2) { value.getComp(it).toFloat() }
                if (ImGui.dragFloat2(name, values)) {
                    property[instance] = if (value is Vector2f) Vector2f(values) else Vector2d(values)
                }
            }
            "Vector3f", "Vector3d" -> {
                val value = value0 as Vector
                val values = FloatArray(3) { value.getComp(it).toFloat() }
                if (ImGui.dragFloat3(name, values)) {
                    property[instance] = if (value is Vector3f) Vector3f(values) else Vector3d(values)
                }
            }
            "Vector4f", "Vector4d" -> {
                val value = value0 as Vector
                val values = FloatArray(4) { value.getComp(it).toFloat() }
                if (ImGui.dragFloat4(name, values)) {
                    property[instance] =
                        if (value is Vector4f) Vector4f(values)
                        else Vector4d(values.map { it.toDouble() }.toDoubleArray())
                }
            }
            "Quaternionf", "Quaterniond" -> {
                val value = when (value0) {
                    is Quaternionf -> value0.getEulerAnglesYXZ(Vector3f())
                    is Quaterniond -> Vector3f(value0.getEulerAnglesYXZ(Vector3d()))
                    else -> Vector3f()
                }
                val vs = FloatArray(3) { value.getComp(it).toDegrees().toFloat() }
                if (ImGui.dragFloat3(name, vs)) {
                    for (i in vs.indices) {
                        vs[i] = vs[i].toRadians()
                    }
                    property[instance] =
                        if (value0 is Quaternionf) Quaternionf().rotationYXZ(vs[1], vs[0], vs[2])
                        else Quaterniond().rotationYXZ(vs[1].toDouble(), vs[0].toDouble(), vs[2].toDouble())
                }
            }
            "FileReference" -> {
                val value = value0.toString()
                val imString = imStrings.getOrPut(name) { ImString(value) }
                if (ImGui.inputText(name, imString)) {
                    property[instance] = getReference(imString.get())
                }
            }
            else -> {
                when (value0) {
                    is Enum<*> -> {
                        val entries = EnumInput.getEnumConstants(value0.javaClass)
                        val index = imInts.getOrPut(name) { ImInt(entries.indexOf(value0)) }
                        if (ImGui.combo(name, index, entries.map { it.name }.toTypedArray())) {
                            property[instance] = entries[index.get()]
                        }
                    }
                    is ExtendableEnum -> {
                        val entries = value0.values
                        val index = imInts.getOrPut(name) { ImInt(entries.indexOf(value0)) }
                        if (ImGui.combo(name, index, entries.map { it.nameDesc.name }.toTypedArray())) {
                            property[instance] = entries[index.get()]
                        }
                    }
                    is Inspectable -> {
                        if (ImGui.treeNode(name)) {
                            showPropertyEditor(value0)
                            ImGui.treePop()
                        }
                    }
                    else -> {
                        ImGui.text("Unknown type: $type for $name")
                    }
                }
            }
        }
    }

    companion object {

        val imStrings = HashMap<Any?, ImString>()
        val imInts = HashMap<Any?, ImInt>()
        val imFloats = HashMap<Any?, ImFloat>()
        val imDoubles = HashMap<Any?, ImDouble>()
        val imBools = HashMap<Any?, ImBoolean>()

        @JvmStatic
        fun main(args: Array<String>) {
            // start Rem's Engine using ImGUI
            RenderDoc.loadRenderDoc() // needs to be initialized first
            launch(RemsEngineImGuiEditor())
        }
    }
}