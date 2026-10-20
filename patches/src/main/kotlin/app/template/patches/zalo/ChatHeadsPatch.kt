package app.template.patches.zalo

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.literal
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction

private object ChatModeFlagsFingerprint : Fingerprint(
    name = "<clinit>",
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        fieldAccess(opcode = Opcode.SGET, definingClass = "Landroid/os/Build\$VERSION;", name = "SDK_INT"),
        literal(29),
        literal(30),
        literal(26),
        literal(33),
    ),
)

private object ChatInfoListFingerprint : Fingerprint(
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        methodCall(definingClass = "Lwd1/a;", name = "A", returnType = "Ljava/util/ArrayList;"),
        methodCall(definingClass = "Ljava/util/ArrayList;", name = "addAll"),
    ),
)

private object ChatInfoClickFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Landroidx/recyclerview/widget/RecyclerView;", "I", "Landroid/view/View;"),
    filters = listOf(
        methodCall(definingClass = "Lcom/zing/zalo/ui/chat/rightmenu/ChatInfoView;", name = "z6"),
        methodCall(definingClass = "Lwd1/a;", name = "L", parameters = listOf("Lwd1/i;")),
    ),
)

private object MiniChatReceiverFingerprint : Fingerprint(
    name = "onCreate",
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        methodCall(definingClass = "Landroid/content/IntentFilter;", name = "addAction"),
        methodCall(definingClass = "Landroid/content/Context;", name = "registerReceiver", parameters = listOf("Landroid/content/BroadcastReceiver;", "Landroid/content/IntentFilter;")),
    ),
)

private object ChatInfoCommitFingerprint : Fingerprint(
    name = "run",
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        fieldAccess(opcode = Opcode.IGET_OBJECT, definingClass = "Lcom/zing/zalo/ui/chat/rightmenu/ChatInfoView;", name = "W0"),
        methodCall(definingClass = "Ljava/util/ArrayList;", name = "clear"),
        methodCall(definingClass = "Ljava/util/ArrayList;", name = "addAll"),
        methodCall(definingClass = "Landroidx/recyclerview/widget/w0;", name = "p"),
    ),
)

private object ChatPostureFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Landroid/content/Context;", "Landroidx/lifecycle/l0;", "Lkb1/a;"),
    filters = listOf(
        methodCall(definingClass = "Lw6/h;", name = "a", parameters = listOf("Landroid/content/Context;")),
        methodCall(definingClass = "Lw6/k;", name = "<init>", parameters = listOf("Lw6/m;", "Landroid/content/Context;", "Lkotlin/coroutines/Continuation;")),
    ),
)

@Suppress("unused")
val chatHeadsPatch = bytecodePatch(
    name = "Chat heads instead of Android bubbles",
    description = "Use Zalo's overlay chat heads rather than native Android bubbles on Android 11 and newer.",
    default = false,
) {
    compatibleWith(Compatibility(
        name = "Zalo",
        packageName = "com.zing.zalo",
        apkFileType = ApkFileType.APKM,
        appIconColor = 0x0068FF,
        targets = listOf(AppTarget(version = "26.08.01")),
    ))

    dependsOn(nativeSignaturePatch)
    extendWith("extensions/extension.mpe")

    execute {
        check(ChatModeFlagsFingerprint.matchAll(1..1).size == 1)
        val initializer = ChatModeFlagsFingerprint.method
        val instructions = initializer.implementation!!.instructions.toList()
        check(initializer.implementation!!.registerCount >= 2)
        val legacyFlag = "Lxy/a;->a:Z"
        val nativeFlag = "Lxy/a;->b:Z"
        check(instructions.count { it.opcode == Opcode.SPUT_BOOLEAN && (it as ReferenceInstruction).reference.toString() == legacyFlag } == 1)
        check(instructions.count { it.opcode == Opcode.SPUT_BOOLEAN && (it as ReferenceInstruction).reference.toString() == nativeFlag } == 1)
        val returnIndex = instructions.indexOfLast { it.opcode == Opcode.RETURN_VOID }
        check(returnIndex == instructions.lastIndex)
        initializer.addInstructionsWithLabels(returnIndex, """
            sget v0, Landroid/os/Build${'$'}VERSION;->SDK_INT:I
            const/16 v1, 0x1e
            if-lt v0, v1, :zalo_original_chat_mode
            const/4 v0, 0x1
            sput-boolean v0, $legacyFlag
            const/4 v0, 0x0
            sput-boolean v0, $nativeFlag
        """.trimIndent(), ExternalLabel("zalo_original_chat_mode", instructions[returnIndex]))

        check(ChatInfoListFingerprint.matchAll(1..1).size == 1)
        val listBuilder = ChatInfoListFingerprint.method
        val listInstructions = listBuilder.implementation!!.instructions.toList()
        val addAllIndex = listInstructions.indexOfFirst {
            it.opcode == Opcode.INVOKE_VIRTUAL && (it as ReferenceInstruction).reference.toString() ==
                "Ljava/util/ArrayList;->addAll(Ljava/util/Collection;)Z"
        }
        check(addAllIndex > 0 && listInstructions[addAllIndex - 1].opcode == Opcode.MOVE_RESULT_OBJECT)
        listBuilder.addInstructionsWithLabels(addAllIndex, "invoke-static {v3}, Lapp/template/extension/extension/ChatHeadAction;->addOption(Ljava/util/ArrayList;)V")

        check(ChatInfoCommitFingerprint.matchAll(1..1).size == 1)
        val commit = ChatInfoCommitFingerprint.method
        check(commit.implementation!!.registerCount == 24)
        val commitInstructions = commit.implementation!!.instructions.toList()
        val commitIndex = commitInstructions.indexOfFirst {
            it.opcode == Opcode.INVOKE_VIRTUAL && (it as ReferenceInstruction).reference.toString() ==
                "Ljava/util/ArrayList;->addAll(Ljava/util/Collection;)Z"
        }
        check(commitIndex > 0)
        commit.addInstructionsWithLabels(commitIndex + 1, "invoke-static {v4, v5}, Lapp/template/extension/extension/ChatHeadAction;->logOptionsCommitted(Ljava/util/ArrayList;Ljava/util/ArrayList;)V")

        check(ChatInfoClickFingerprint.matchAll(1..1).size == 1)
        val click = ChatInfoClickFingerprint.method
        check(click.implementation!!.registerCount == 4)
        val clickInstructions = click.implementation!!.instructions.toList()
        val dispatchIndex = clickInstructions.indexOfFirst {
            it.opcode == Opcode.INVOKE_VIRTUAL && (it as ReferenceInstruction).reference.toString() ==
                "Lwd1/a;->L(Lwd1/i;)V"
        }
        check(dispatchIndex > 0)
        click.addInstructionsWithLabels(dispatchIndex, """
            invoke-static {p3, p1}, Lapp/template/extension/extension/ChatHeadAction;->handleOption(Ljava/lang/Object;Ljava/lang/Object;)Z
            move-result p2
            if-eqz p2, :zalo_original_chat_info_action
            return-void
        """.trimIndent(), ExternalLabel("zalo_original_chat_info_action", clickInstructions[dispatchIndex]))

        check(MiniChatReceiverFingerprint.matchAll(1..1).size == 1)
        val service = MiniChatReceiverFingerprint.method
        check(service.implementation!!.registerCount == 3)
        val receiverInstructions = service.implementation!!.instructions.toList()
        val receiverIndex = receiverInstructions.indexOfFirst {
            it.opcode == Opcode.INVOKE_VIRTUAL && (it as ReferenceInstruction).reference.toString() ==
                "Landroid/content/Context;->registerReceiver(Landroid/content/BroadcastReceiver;Landroid/content/IntentFilter;)Landroid/content/Intent;"
        }
        check(receiverIndex > 0)
        service.addInstructionsWithLabels(receiverIndex, "invoke-static {p0, v1, v0}, Lapp/template/extension/extension/ChatHeadAction;->registerMiniChatReceiver(Landroid/content/Context;Landroid/content/BroadcastReceiver;Landroid/content/IntentFilter;)V")
        service.removeInstruction(receiverIndex + 1)

        check(ChatPostureFingerprint.matchAll(1..1).size == 1)
        val posture = ChatPostureFingerprint.method
        check(posture.implementation!!.registerCount == 19)
        val postureInstructions = posture.implementation!!.instructions.toList()
        val flowIndex = postureInstructions.indexOfFirst {
            it.opcode == Opcode.INVOKE_STATIC && (it as ReferenceInstruction).reference.toString() ==
                "Lw6/h;->a(Landroid/content/Context;)Lw6/m;"
        }
        check(flowIndex > 0 && postureInstructions[flowIndex - 1].opcode == Opcode.INVOKE_VIRTUAL)
        posture.addInstructionsWithLabels(flowIndex, """
            invoke-static {v1}, Lapp/template/extension/extension/ChatHeadAction;->canObservePosture(Landroid/content/Context;)Z
            move-result v0
            if-nez v0, :zalo_observe_posture
            return-void
        """.trimIndent(), ExternalLabel("zalo_observe_posture", postureInstructions[flowIndex]))
    }
}