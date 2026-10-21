package app.template.patches.zalo

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction

private object ZaloTypefaceFactoryFingerprint : Fingerprint(
    returnType = "Landroid/graphics/Typeface;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    parameters = listOf("Landroid/content/Context;", "I"),
    filters = listOf(
        methodCall(definingClass = "Ljava/util/concurrent/ConcurrentHashMap;", name = "get"),
        string("fonts/Roboto-Regular.ttf"),
        methodCall(definingClass = "Landroid/graphics/Typeface;", name = "createFromAsset"),
    ),
)

private object ZaloFontStartupFingerprint : Fingerprint(
    definingClass = "Lcom/zing/zalo/MainApplication;",
    returnType = "V",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = emptyList(),
    filters = listOf(
        string("CURRENT_USER_UID_FLOW_RESTORE_APP"),
        methodCall(definingClass = "Ljava/util/concurrent/atomic/AtomicBoolean;", name = "set"),
    ),
)

private object ZaloMessageStyleFingerprint : Fingerprint(
    returnType = "V",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf("Lbv1/b;"),
    filters = listOf(
        methodCall(definingClass = "Lo62/b0;", name = "h"),
        methodCall(definingClass = "Lkn1/i;", name = "K0"),
    ),
)

@Suppress("unused")
val customFontPatch = bytecodePatch(
    name = "Custom font",
    description = "Choose a font in Nezumi's Settings for Zalo's factory-backed text.",
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
        check(ZaloTypefaceFactoryFingerprint.matchAll(1..1).size == 1)
        check(ZaloFontStartupFingerprint.matchAll(1..1).size == 1)
        check(ZaloMessageStyleFingerprint.matchAll(1..1).size == 1)
        val method = ZaloTypefaceFactoryFingerprint.method
        check(method.implementation!!.registerCount >= 4)
        val original = method.implementation!!.instructions.first()
        method.addInstructionsWithLabels(0, """
            invoke-static {p0, p1}, Lapp/template/extension/extension/CustomFont;->getTypeface(Landroid/content/Context;I)Landroid/graphics/Typeface;
            move-result-object v0
            if-eqz v0, :nezumi_original_typeface
            return-object v0
        """.trimIndent(), ExternalLabel("nezumi_original_typeface", original))

        val startup = ZaloFontStartupFingerprint.method
        check(startup.implementation!!.registerCount >= 19)
        val flagIndex = startup.implementation!!.instructions.indexOfFirst {
            it.opcode == Opcode.SGET_OBJECT &&
                (it as? ReferenceInstruction)?.reference?.toString() ==
                "Lcom/zing/zalo/ui/widget/h2;->a:Ljava/util/concurrent/atomic/AtomicBoolean;"
        }
        check(flagIndex >= 0)
        val flagSetter = startup.implementation!!.instructions.elementAt(flagIndex + 1)
        check(flagSetter.opcode == Opcode.INVOKE_VIRTUAL &&
            (flagSetter as? ReferenceInstruction)?.reference?.toString() ==
            "Ljava/util/concurrent/atomic/AtomicBoolean;->set(Z)V")
        startup.addInstructions(flagIndex + 2, """
            invoke-static {v1, v10}, Lapp/template/extension/extension/CustomFont;->enableSelectedFont(Landroid/content/Context;Ljava/util/concurrent/atomic/AtomicBoolean;)V
        """.trimIndent())

        val messageStyle = ZaloMessageStyleFingerprint.method
        check(messageStyle.implementation!!.registerCount >= 9)
        val instructions = messageStyle.implementation!!.instructions
        val typefaceCall = instructions.indexOfFirst { instruction ->
            instruction.opcode == Opcode.INVOKE_STATIC &&
                (instruction as? ReferenceInstruction)?.reference?.toString() ==
                "Lo62/b0;->h(Landroid/content/Context;Ljava/lang/String;Ljava/lang/Integer;)Landroid/graphics/Typeface;" &&
                (instruction as? FiveRegisterInstruction)?.registerC == 1 &&
                instructions.indexOf(instruction).let { index ->
                    index + 2 < instructions.size &&
                        (instructions.elementAt(index + 2) as? ReferenceInstruction)?.reference?.toString() ==
                        "Lkn1/i;->K0(Landroid/graphics/Typeface;)V"
                }
        }
        check(typefaceCall >= 0)
        val result = instructions.elementAt(typefaceCall + 1)
        val setter = instructions.elementAt(typefaceCall + 2)
        check(result.opcode == Opcode.MOVE_RESULT_OBJECT &&
            (result as? OneRegisterInstruction)?.registerA == 1)
        check(setter.opcode == Opcode.INVOKE_VIRTUAL &&
            (setter as? ReferenceInstruction)?.reference?.toString() ==
            "Lkn1/i;->K0(Landroid/graphics/Typeface;)V" &&
            (setter as? FiveRegisterInstruction)?.registerC == 0 && setter.registerD == 1)
        messageStyle.addInstructionsWithLabels(typefaceCall + 2, """
            instance-of v3, v0, Lru1/l;
            if-eqz v3, :nezumi_original_message_font
            iget-object v3, v0, Lhn1/j;->c:Landroid/content/Context;
            invoke-static {v3, v1}, Lapp/template/extension/extension/CustomFont;->forMessageModule(Landroid/content/Context;Landroid/graphics/Typeface;)Landroid/graphics/Typeface;
            move-result-object v1
        """.trimIndent(), ExternalLabel("nezumi_original_message_font", setter))
        attachNezumiSettings()
    }
}