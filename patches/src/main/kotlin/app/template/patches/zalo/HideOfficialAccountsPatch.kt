package app.template.patches.zalo

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.literal
import app.morphe.patcher.methodCall
import app.morphe.patcher.string
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private object MessagesListFingerprint : Fingerprint(
    returnType = "Ljava/util/List;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf("Ljava/util/ArrayList;"),
    filters = listOf(
        string("LatestMention"),
        methodCall(definingClass = "Ljava/util/ArrayList;", name = "add"),
        literal(45),
    ),
)

// Chat-notification entry (q44.f(ContactProfile, vv3, Z, Z)V). Runs before the canEnterNotificationFlow
// gate, whose server forceNotify check returns true ahead of the native mute test.
private object ChatNotificationEntryFingerprint : Fingerprint(
    returnType = "V",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf("Lcom/zing/zalo/control/ContactProfile;", "L", "Z", "Z"),
    filters = listOf(
        string("checkShowNotificationInternal ignore noti: "),
        string("Skip noti for msg ("),
    ),
)

@Suppress("unused")
val hideOfficialAccountsPatch = bytecodePatch(
    name = "Hide OA accounts in Messages",
    description = "Hide OA accounts in Messages and/or mute their notifications. Both options are configurable in Nezumi Settings.",
    default = true,
) {
    compatibleWith(Compatibility(
        name = "Zalo",
        packageName = "com.zing.zalo",
        apkFileType = ApkFileType.APKM,
        appIconColor = 0x0068FF,
        targets = listOf(AppTarget(version = "26.11.01")),
    ))

    dependsOn(nativeSignaturePatch)
    extendWith("extensions/extension.mpe")

    execute {
        registerApplied(PatchId.HIDE_OA_ACCOUNTS_IN_MESSAGES)
        check(MessagesListFingerprint.matchAll(1..1).size == 1)
        val method = MessagesListFingerprint.method
        val instructions = method.implementation!!.instructions.toList()
        val returns = instructions.indices.filter { instructions[it].opcode == Opcode.RETURN_OBJECT }
        check(returns.size in 2..3)
        returns.reversed().forEach { index ->
            val register = (instructions[index] as OneRegisterInstruction).registerA
            check(register < 16)
            method.replaceInstruction(index,
                "invoke-static {v$register}, $OFFICIAL_ACCOUNTS->filterOfficialAccounts(Ljava/util/List;)Ljava/util/List;")
            method.addInstructions(index + 1, "move-result-object v$register\nreturn-object v$register")
        }

        check(ChatNotificationEntryFingerprint.matchAll(1..1).size == 1)
        val notification = ChatNotificationEntryFingerprint.method
        val notificationImpl = notification.implementation!!
        // Five parameter registers (this, profile, message, Z, Z) sit above v0, which is free at entry
        // (the first instruction overwrites it).
        check(notificationImpl.registerCount >= 6)
        val originalStart = notificationImpl.instructions.first()
        notification.addInstructionsWithLabels(0, """
            invoke-static {p1, p2}, $OFFICIAL_ACCOUNTS->shouldSuppressNotification(Ljava/lang/Object;Ljava/lang/Object;)Z
            move-result v0
            if-eqz v0, :nezumi_original_notification
            return-void
        """.trimIndent(), ExternalLabel("nezumi_original_notification", originalStart))

        registerHooks(PatchId.HIDE_OA_ACCOUNTS_IN_MESSAGES, method, notification)
        attachNezumiSettings("attachOAMessages")
    }
}