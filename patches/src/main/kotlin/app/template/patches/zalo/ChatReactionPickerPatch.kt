package app.template.patches.zalo

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction

private const val SETTINGS_CLASS = "Lapp/template/extension/extension/NezumiSettings;"

private object ChatReactionEntitlementFingerprint : Fingerprint(
    returnType = "Z",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = emptyList(),
    filters = listOf(
        fieldAccess(opcode = Opcode.IGET_BOOLEAN, definingClass = "this", type = "Z"),
        string("subscribePackageIdConfig"),
    ),
)

internal object ZaloSettingsFingerprint : Fingerprint(
    definingClass = "Lcom/zing/zalo/ui/settings/SettingV2View;",
    name = "E6",
    returnType = "Landroid/view/View;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf("Landroid/view/LayoutInflater;", "Landroid/widget/LinearLayout;", "Landroid/os/Bundle;"),
    filters = listOf(
        fieldAccess(opcode = Opcode.SGET, definingClass = "Lei/u2;", name = "setting_v2_view"),
        methodCall(definingClass = "Landroid/view/LayoutInflater;", name = "inflate"),
        fieldAccess(opcode = Opcode.SGET, definingClass = "Lei/s2;", name = "itemPrivacy"),
    ),
)

@Suppress("unused")
val chatReactionPickerPatch = bytecodePatch(
    name = "Zalo chat reaction picker",
    description = "Allow the enabled chat reaction UI without a matching subscription package.",
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
        check(ChatReactionEntitlementFingerprint.matchAll(1..1).size == 1)
        check(ZaloSettingsFingerprint.matchAll(1..1).size == 1)
        val method = ChatReactionEntitlementFingerprint.method
        val first = method.getInstruction<OneRegisterInstruction>(0)
        val branch = method.getInstruction<OneRegisterInstruction>(1)
        check(first.opcode == Opcode.IGET_BOOLEAN && branch.opcode == Opcode.IF_EQZ)
        check(first.registerA == branch.registerA)
        method.addInstructionsWithLabels(1, """
            invoke-static {}, Lcom/zing/zalo/MainApplication;->getAppContext()Landroid/content/Context;
            move-result-object v1
            invoke-static {v1}, $SETTINGS_CLASS->isReactionEnabled(Landroid/content/Context;)Z
            move-result v1
            if-eqz v1, :nezumi_original_reaction
            return v${first.registerA}
        """.trimIndent(), ExternalLabel("nezumi_original_reaction", branch))

        attachNezumiSettings()
    }
}

context(_: BytecodePatchContext)
internal fun attachNezumiSettings() {
    check(ZaloSettingsFingerprint.matchAll(1..1).size == 1)
    val settings = ZaloSettingsFingerprint.method
    val privacyIndex = settings.implementation!!.instructions.indexOfFirst {
        it.opcode == Opcode.SGET &&
            (it as? ReferenceInstruction)?.reference?.toString() == "Lei/s2;->itemPrivacy:I"
    }
    check(privacyIndex >= 0)
    check(settings.getInstruction<OneRegisterInstruction>(privacyIndex + 2).opcode == Opcode.MOVE_RESULT_OBJECT)
    check(settings.getInstruction<OneRegisterInstruction>(privacyIndex + 3).opcode == Opcode.CHECK_CAST)
    check(settings.getInstruction<OneRegisterInstruction>(privacyIndex + 4).opcode == Opcode.IF_EQZ)
    settings.addInstructions(privacyIndex + 5, "invoke-static {v2, v11}, $SETTINGS_CLASS->attach(Landroid/view/View;Landroid/view/View;)V")
}