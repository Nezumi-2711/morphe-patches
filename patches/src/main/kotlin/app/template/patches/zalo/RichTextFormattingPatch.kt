package app.template.patches.zalo

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterWithin
import app.morphe.patcher.checkCast
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.patch
import app.morphe.patcher.patch.classDefBy
import app.morphe.patcher.patch.mutableClassDefBy
import app.morphe.patcher.string
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.BuilderOffsetInstruction
import com.android.tools.smali.dexlib2.builder.BuilderSwitchPayload
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ThreeRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

private const val CHAT_VIEW = "Lcom/zing/zalo/ui/chat/ChatView;"
private const val CHAT_INPUT_BAR = "Lcom/zing/zalo/ui/chat/widget/inputbar/ChatInputBar;"
private const val FRAME_LAYOUT = "Landroid/widget/FrameLayout;"
private const val LINEAR_LAYOUT = "Landroid/widget/LinearLayout;"
private const val MENTION_SPAN = "Lcom/zing/zalo/social/presentation/callback_span/MentionSpan;"
private const val MESSAGE_ID = "Lcom/zing/zalo/data/entity/chat/message/MessageId;"
private const val RICH_TEXT_FORMAT = "Lcom/zing/zalo/control/RichTextFormat;"
private const val STRING = "Ljava/lang/String;"

// Message.isRichText(): `msgType == 0 && content.action == "rtf" && content instanceof TextContent
// && !((TextContent) content).styles.isEmpty()`. Only three methods in 26.11.01 load "rtf"; this is
// the only one returning boolean. Yields the obfuscated message, text-content and content-field types.
private object RichTextGateFingerprint : Fingerprint(
    returnType = "Z",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf(),
    filters = listOf(
        string("rtf"),
        methodCall(definingClass = "Landroid/text/TextUtils;", name = "equals"),
        opcode(Opcode.INSTANCE_OF),
        opcode(Opcode.CHECK_CAST),
        methodCall(definingClass = "Ljava/util/List;", name = "isEmpty"),
    ),
)

// Chat composer send for a type-0 text message: builds the message, lays it out, applies the
// reminder detection flags and queues it. "1000403" (analytics) is unique in the APK.
private object SendTextMessageFingerprint : Fingerprint(
    returnType = "V",
    accessFlags = listOf(AccessFlags.PUBLIC),
    parameters = listOf(STRING, STRING, "L", "L", "L", "Z", "Z", "L", "L"),
    filters = listOf(
        methodCall(definingClass = STRING, name = "trim"),
        string("ENABLE_REMINDER_FROM_MESSAGE"),
        string("SRC_DETECT_TIME_REMINDER_FROM_MESSAGE"),
        string("1000403"),
    ),
)

// Composer flattening: copies the composer Editable into a StringBuilder and replaces each mention
// span with its wire text. "1000310" (mention analytics) is unique in the APK.
private object FlattenComposerFingerprint : Fingerprint(
    definingClass = CHAT_VIEW,
    returnType = STRING,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf("Landroid/text/Editable;", "L"),
    filters = listOf(
        methodCall(definingClass = "Landroid/text/Spanned;", name = "getSpans"),
        string("1000310"),
    ),
)

// Zalo's rtf style token parser: reads the font size range and color whitelist from remote config.
private object TextStyleParseFingerprint : Fingerprint(
    definingClass = "Lcom/zing/zalo/control/TextStyle;",
    returnType = "Lcom/zing/zalo/control/TextStyle;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    parameters = listOf(STRING),
    filters = listOf(
        string("chat@message@rtf_configs@rtf_font_size_min"),
        methodCall(opcode = Opcode.INVOKE_STATIC, parameters = listOf(STRING, "I"), returnType = "I",
            location = MatchAfterWithin(2)),
        string("chat@message@rtf_configs@rtf_font_color_whitelist"),
        methodCall(opcode = Opcode.INVOKE_STATIC, parameters = listOf(STRING, STRING), returnType = STRING,
            location = MatchAfterWithin(2)),
    ),
)

// Chat input bar constructor (stable class name). It builds the input row, the right-functions group
// and the send button, then turns off motion-event splitting on its rows as its last statements.
private object ChatInputBarInitFingerprint : Fingerprint(
    definingClass = CHAT_INPUT_BAR,
    name = "<init>",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
    parameters = listOf("Landroid/content/Context;", STRING, "L", "L", "L"),
    filters = listOf(
        methodCall(definingClass = "Landroid/view/ViewGroup;", name = "setMotionEventSplittingEnabled"),
    ),
)

// Refills the right-functions group (more / mic / image) next to the composer; kept name in 26.11.01.
private object RightFunctionsFingerprint : Fingerprint(
    definingClass = CHAT_INPUT_BAR,
    name = "setupLayoutRightFunctions",
    returnType = "V",
    accessFlags = listOf(AccessFlags.PUBLIC),
    parameters = listOf("L"),
    filters = listOf(
        methodCall(definingClass = "Landroid/view/ViewGroup;", name = "removeAllViews"),
        methodCall(definingClass = "Landroid/view/ViewGroup;", name = "addView",
            parameters = listOf("Landroid/view/View;", "I", "I")),
    ),
)

// Rich-text content parse: `if (action == "rtf" && params not empty) styles = new JSONObject(params)
// .optJSONArray("styles")` mapped through RichTextFormat (stable class name). The field fed to
// `new JSONObject(String)` is the content's params field.
private object RichTextParamsFingerprint : Fingerprint(
    returnType = "V",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf(),
    filters = listOf(
        string("rtf"),
        methodCall(definingClass = "Lorg/json/JSONObject;", name = "<init>", parameters = listOf(STRING)),
        string("styles"),
        methodCall(definingClass = RICH_TEXT_FORMAT, returnType = RICH_TEXT_FORMAT),
    ),
)

// Message context menu "Copy": collects the copy options of a message (original text, link, caption,
// translation), runs the only one directly or shows a chooser. "ChatDialogView" is its error log tag.
private object MessageCopyFingerprint : Fingerprint(
    returnType = "V",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    parameters = listOf("L", "L"),
    filters = listOf(
        fieldAccess(opcode = Opcode.IGET_OBJECT, definingClass = MESSAGE_ID, type = STRING),
        methodCall(definingClass = "Ljava/util/ArrayList;", name = "size"),
        methodCall(definingClass = "Ljava/util/ArrayList;", name = "toArray"),
        checkCast("[Ljava/lang/CharSequence;"),
        string("ChatDialogView"),
    ),
)

private val Instruction.methodReference get() = (this as? ReferenceInstruction)?.reference as? MethodReference
private val Instruction.fieldReference get() = (this as? ReferenceInstruction)?.reference as? FieldReference

@Suppress("unused")
val richTextFormattingPatch = patch(
    name = "Rich text formatting",
    description = "Adds a formatting toolbar (Aa) to the message composer and sends bold, italic, underline, " +
        "strikethrough, size, color, lists and indent as rich text that the Zalo app and Zalo Web display. " +
        "Copying a formatted message or composer text and pasting it into the composer keeps the formatting. " +
        "Toggle in Nezumi's Settings.",
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
        registerApplied(PatchId.RICH_TEXT_FORMATTING)
        check(RichTextGateFingerprint.matchAll(1..1).size == 1)
        check(SendTextMessageFingerprint.matchAll(1..1).size == 1)
        check(FlattenComposerFingerprint.matchAll(1..1).size == 1)
        check(TextStyleParseFingerprint.matchAll(1..1).size == 1)
        check(ChatInputBarInitFingerprint.matchAll(1..1).size == 1)
        check(RightFunctionsFingerprint.matchAll(1..1).size == 1)
        check(RichTextParamsFingerprint.matchAll(1..1).size == 1)
        check(MessageCopyFingerprint.matchAll(1..1).size == 1)

        // 1. Type discovery from the rich-text gate.
        val gate = RichTextGateFingerprint.originalMethod
        val message = gate.definingClass
        val gateCode = gate.implementation!!.instructions.toList()
        val textContent = (gateCode[RichTextGateFingerprint.instructionMatches[2].index] as ReferenceInstruction)
            .reference.let { (it as TypeReference).type }
        val content = mutableClassDefBy(textContent).superclass!!
        val contentField = gateCode.firstNotNullOf { instruction ->
            if (instruction.opcode != Opcode.IGET_OBJECT) return@firstNotNullOf null
            instruction.fieldReference?.takeIf { it.definingClass == message && it.type == content }
        }.let { "${it.definingClass}->${it.name}:${it.type}" }
        check(mutableClassDefBy(textContent).methods.any {
            it.name == "<init>" && it.parameterTypes.map { type -> type.toString() } == listOf("Lorg/json/JSONObject;")
        })

        // 2. Remote-config getters for the size range and color whitelist (extension stubs call them).
        val styleMatches = TextStyleParseFingerprint.instructionMatches
        val remoteInt = styleMatches[1].instruction.methodReference!!
        val remoteString = styleMatches[3].instruction.methodReference!!
        val config = mutableClassDefBy(RICH_TEXT_CONFIG)
        fun replaceStub(name: String, body: String) {
            val stub = config.methods.single { it.name == name }
            check(stub.implementation!!.registerCount == 2) { "Unexpected $name stub registers" }
            val oldSize = stub.implementation!!.instructions.size
            stub.addInstructions(0, body.trimIndent())
            stub.removeInstructions(stub.implementation!!.instructions.size - oldSize, oldSize)
        }
        replaceStub("remoteInt", """
            invoke-static {p0, p1}, $remoteInt
            move-result p0
            return p0
        """)
        replaceStub("remoteString", """
            invoke-static {p0, p1}, $remoteString
            move-result-object p0
            return-object p0
        """)

        // 3. Composer flattening: capture the toolbar spans at entry, hand off the real result at exit.
        val flatten = FlattenComposerFingerprint.method
        val flattenCode = flatten.implementation!!.instructions.toList()
        // The extension reads these mention span fields reflectively: they must be the ones read here.
        val mentionFields = flattenCode.mapNotNull { instruction ->
            instruction.fieldReference?.takeIf { it.definingClass == MENTION_SPAN }?.let { "${it.name}:${it.type}" }
        }.toSet()
        check(mentionFields.containsAll(listOf("b:I", "c:I", "e:$STRING", "f:$STRING"))) {
            "Rich text: mention span fields changed ($mentionFields)"
        }
        check(classDefBy(MENTION_SPAN).fields.filter { AccessFlags.PUBLIC.isSet(it.accessFlags) }
            .map { "${it.name}:${it.type}" }.containsAll(listOf("b:I", "c:I", "e:$STRING", "f:$STRING")))
        // The only normal return: `sb.toString()` / move-result-object / return-object.
        val returnIndex = flattenCode.indices.single { index ->
            flattenCode[index].opcode == Opcode.RETURN_OBJECT && index >= 2 &&
                flattenCode[index - 1].opcode == Opcode.MOVE_RESULT_OBJECT &&
                flattenCode[index - 2].methodReference?.let {
                    it.definingClass == "Ljava/lang/StringBuilder;" && it.name == "toString"
                } == true
        }
        val resultRegister = flatten.getInstruction<OneRegisterInstruction>(returnIndex).registerA
        check(flatten.getInstruction<OneRegisterInstruction>(returnIndex - 1).registerA == resultRegister)
        // Both hooks must run on every pass: neither the entry nor the return may be a jump target.
        flatten.requireFallThrough(0)
        flatten.requireFallThrough(returnIndex)
        flatten.addInstruction(returnIndex,
            "invoke-static {v$resultRegister}, $RICH_TEXT_SEND->afterFlatten(Ljava/lang/String;)V")
        // p1 (the Editable) is overwritten by the mention span array, so capture before anything runs.
        flatten.addInstruction(0, "invoke-static {p1}, $RICH_TEXT_SEND->beforeFlatten(Landroid/text/Editable;)V")

        // 4. Message build: attach the rtf content between `message = builder.build()` and
        //    `message.layout()` (Q5 in 26.11.01), so the sender's own bubble is laid out with the styles.
        val send = SendTextMessageFingerprint.method
        val code = send.implementation!!.instructions.toList()
        val buildIndex = code.indexOfFirst {
            it.opcode == Opcode.INVOKE_VIRTUAL && it.methodReference?.let { ref ->
                ref.returnType == message && ref.parameterTypes.isEmpty()
            } == true
        }
        check(buildIndex >= 0)
        check(code[buildIndex + 1].opcode == Opcode.MOVE_RESULT_OBJECT)
        val messageRegister = send.getInstruction<OneRegisterInstruction>(buildIndex + 1).registerA
        val layoutIndex = buildIndex + 2
        val layout = code[layoutIndex]
        check(layout.opcode == Opcode.INVOKE_VIRTUAL)
        check(layout.methodReference!!.let {
            it.definingClass == message && it.parameterTypes.isEmpty() && it.returnType == "V"
        })
        check(send.getInstruction<FiveRegisterInstruction>(layoutIndex).registerC == messageRegister)

        // Scratch registers v3/v4: dead here in 26.11.01 (next access to each is a plain write).
        // Text is p1, unchanged until after this point.
        // The check is linear: it follows the instruction list from the hook, not the control flow, so
        // it relies on the verified 26.11.01 shape (no branch between the hook and the first write of
        // v3/v4 leads to a read of them). It also rejects any wide (long/double) instruction touching
        // v3 or v4 as half of a vN/vN+1 pair (operands v2..v4) before that write.
        check(send.implementation!!.registerCount == 15)
        check(messageRegister != 3 && messageRegister != 4)
        listOf(3, 4).forEach { register ->
            val tail = code.drop(layoutIndex)
            val firstUse = tail.indexOfFirst { instruction -> instruction.usesRegister(register) }
            check(firstUse >= 0) { "Rich text: v$register is never written after the send hook" }
            tail.take(firstUse + 1).firstOrNull { it.touchesWidePair(register) }?.let { wide ->
                error("Rich text: wide ${wide.opcode} touches v$register before it is written at the send hook")
            }
            val next = tail[firstUse]
            check(next.opcode == Opcode.CONST_4 || next.opcode == Opcode.CONST_STRING) {
                "Rich text: v$register is not free at the send hook (${next.opcode})"
            }
            check((next as OneRegisterInstruction).registerA == register)
        }
        send.requireFallThrough(layoutIndex)

        send.addInstructionsWithLabels(layoutIndex, """
            invoke-static {p1}, $RICH_TEXT_SEND->buildRtfContent(Ljava/lang/String;)Lorg/json/JSONObject;
            move-result-object v3
            if-eqz v3, :nezumi_rtf_skip
            new-instance v4, $textContent
            invoke-direct {v4, v3}, $textContent-><init>(Lorg/json/JSONObject;)V
            iput-object v4, v$messageRegister, $contentField
        """.trimIndent(), ExternalLabel("nezumi_rtf_skip", send.getInstruction(layoutIndex)))

        // 5. Input bar constructor: create the toolbar row once every child view exists. Inserted
        //    before `iget-object vRow, vThis, ChatInputBar->(input row):FrameLayout` that feeds the first
        //    setMotionEventSplittingEnabled; vThis is the `this` copy made at entry.
        val init = ChatInputBarInitFingerprint.method
        val initCode = init.implementation!!.instructions.toList()
        val splitIndex = ChatInputBarInitFingerprint.instructionMatches[0].index
        val rowIndex = splitIndex - 1
        val rowRead = initCode[rowIndex]
        check(rowRead.opcode == Opcode.IGET_OBJECT) { "Rich text: unexpected input bar constructor tail (${rowRead.opcode})" }
        check(rowRead.fieldReference!!.let { it.definingClass == CHAT_INPUT_BAR && it.type == FRAME_LAYOUT })
        check(init.getInstruction<FiveRegisterInstruction>(splitIndex).registerC ==
            (rowRead as TwoRegisterInstruction).registerA)
        val barRegister = rowRead.registerB
        val thisRegister = init.implementation!!.registerCount - 6 // p0; 5 parameters, none wide
        check(initCode[0].opcode == Opcode.MOVE_OBJECT_FROM16 &&
            (initCode[0] as TwoRegisterInstruction).let { it.registerA == barRegister && it.registerB == thisRegister }) {
            "Rich text: input bar `this` register changed"
        }
        check(initCode.subList(1, rowIndex).none { it.writesRegister(barRegister) }) {
            "Rich text: v$barRegister is not `this` at the input bar hook"
        }
        init.requireFallThrough(rowIndex)
        init.addInstruction(rowIndex,
            "invoke-static {v$barRegister}, $RICH_TEXT_TOOLBAR->onInputBarCreated($FRAME_LAYOUT)V")

        // 6. Right functions: add the Aa button after Zalo refilled the group. The single return-void is
        //    only reached by jumps (loop exit and the "group full" goto), so replace it in place to keep
        //    both labels on the hook, then return. v0 holds the group for the whole method.
        val rightFunctions = RightFunctionsFingerprint.method
        val rightCode = rightFunctions.implementation!!.instructions.toList()
        val first = rightCode[0]
        check(first.opcode == Opcode.IGET_OBJECT && first.fieldReference!!.let {
            it.definingClass == CHAT_INPUT_BAR && it.type == LINEAR_LAYOUT
        } && (first as TwoRegisterInstruction).registerB == rightFunctions.implementation!!.registerCount - 2) {
            "Rich text: unexpected right-functions prologue"
        }
        val groupRegister = (first as TwoRegisterInstruction).registerA
        check(rightCode.drop(1).none { it.writesRegister(groupRegister) }) {
            "Rich text: v$groupRegister is reassigned in setupLayoutRightFunctions"
        }
        val rightReturn = rightCode.indices.single { rightCode[it].opcode == Opcode.RETURN_VOID }
        rightFunctions.replaceInstruction(rightReturn,
            "invoke-static {p0, v$groupRegister}, $RICH_TEXT_TOOLBAR->onRightFunctions($FRAME_LAYOUT$LINEAR_LAYOUT)V")
        rightFunctions.addInstruction(rightReturn + 1, "return-void")

        // 7. Message copy: stash the styles of a formatted message when its Copy menu action starts.
        //    Fields come from structure, not names: message text = the message String field the send
        //    builder fills from the text parameter; params = the content field kc4.h() parses.
        val sendOriginal = SendTextMessageFingerprint.originalMethod
        val sendOriginalCode = sendOriginal.implementation!!.instructions.toList()
        val textParameter = sendOriginal.implementation!!.registerCount - 9 // p1 of 9 non-wide parameters
        val buildCall = sendOriginalCode[buildIndex].methodReference!!
        check(buildCall.returnType == message && sendOriginalCode[buildIndex].opcode == Opcode.INVOKE_VIRTUAL)
        val builderText = sendOriginalCode.take(buildIndex).mapNotNull { instruction ->
            if (instruction.opcode != Opcode.IPUT_OBJECT || (instruction as TwoRegisterInstruction).registerA != textParameter) {
                return@mapNotNull null
            }
            instruction.fieldReference?.takeIf { it.definingClass == buildCall.definingClass && it.type == STRING }
        }.single().let { "${it.definingClass}->${it.name}:${it.type}" }
        val builderMethod = classDefBy(buildCall.definingClass).methods.single {
            it.name == buildCall.name && it.parameterTypes.isEmpty() && it.returnType == message
        }
        val builderCode = builderMethod.implementation!!.instructions.toList()
        val textField = builderCode.indices.mapNotNull { index ->
            val read = builderCode[index]
            val write = builderCode.getOrNull(index + 1) ?: return@mapNotNull null
            if (read.opcode != Opcode.IGET_OBJECT || write.opcode != Opcode.IPUT_OBJECT) return@mapNotNull null
            val source = read.fieldReference!!
            if ("${source.definingClass}->${source.name}:${source.type}" != builderText) return@mapNotNull null
            if ((write as TwoRegisterInstruction).registerA != (read as TwoRegisterInstruction).registerA) return@mapNotNull null
            write.fieldReference!!.takeIf { it.definingClass == message && it.type == STRING }
        }.single().let { "${it.definingClass}->${it.name}:${it.type}" }

        val paramsParse = RichTextParamsFingerprint.originalMethod
        check(paramsParse.definingClass == textContent) { "Rich text: params parser is not on $textContent" }
        val paramsCode = paramsParse.implementation!!.instructions.toList()
        val jsonIndex = RichTextParamsFingerprint.instructionMatches[1].index
        val paramsRead = paramsCode[jsonIndex - 1]
        check(paramsRead.opcode == Opcode.IGET_OBJECT &&
            (paramsRead as TwoRegisterInstruction).registerA == (paramsCode[jsonIndex] as FiveRegisterInstruction).registerD) {
            "Rich text: params are not read right before new JSONObject(String)"
        }
        val paramsRef = paramsRead.fieldReference!!
        val actionRef = gateCode.first { it.opcode == Opcode.IGET_OBJECT && it.fieldReference!!.type == STRING }.fieldReference!!
        check(paramsRef.definingClass == content && paramsRef.type == STRING && paramsRef.name != actionRef.name) {
            "Rich text: unexpected params field $paramsRef"
        }
        val paramsField = "${paramsRef.definingClass}->${paramsRef.name}:${paramsRef.type}"

        val copy = MessageCopyFingerprint.method
        check(copy.parameterTypes.first().toString() == message) { "Rich text: copy entry does not take $message" }
        check(copy.implementation!!.registerCount - 2 >= 2) { "Rich text: copy entry has no scratch registers" }
        copy.requireFallThrough(0)
        // At entry no local is live yet, so v0/v1 are free. p0 is the message: the gate (isRichText) is
        // side-effect free and guarantees a non-null text content before its params are read.
        copy.addInstructionsWithLabels(0, """
            if-eqz p0, :nezumi_copy_skip
            const/4 v0, 0x0
            invoke-virtual {p0}, $message->${gate.name}()Z
            move-result v1
            if-eqz v1, :nezumi_copy_plain
            iget-object v0, p0, $contentField
            iget-object v0, v0, $paramsField
            :nezumi_copy_plain
            iget-object v1, p0, $textField
            invoke-static {v1, v0}, $RICH_TEXT_CLIPBOARD->onMessageCopy(Ljava/lang/String;Ljava/lang/String;)V
        """.trimIndent(), ExternalLabel("nezumi_copy_skip", copy.getInstruction(0)))

        registerHooks(PatchId.RICH_TEXT_FORMATTING, flatten, send, init, rightFunctions, copy)
        attachNezumiSettings("attachRichText")
    }
}

/** Register operands of [this] as encoded (a wide operand appears once, as the low register of its pair). */
private fun Instruction.registerOperands(): List<Int> = when (this) {
    is FiveRegisterInstruction -> listOf(registerC, registerD, registerE, registerF, registerG).take(registerCount)
    is RegisterRangeInstruction -> (startRegister until startRegister + registerCount).toList()
    is ThreeRegisterInstruction -> listOf(registerA, registerB, registerC)
    is TwoRegisterInstruction -> listOf(registerA, registerB)
    is OneRegisterInstruction -> listOf(registerA)
    else -> emptyList()
}

/** True when [this] reads or writes register [register] (non-range forms; range forms count every slot). */
private fun Instruction.usesRegister(register: Int): Boolean = register in registerOperands()

/**
 * True when [this] is a long/double instruction with an operand that may be a pair covering
 * [register] (the operand itself or the one below it). Conservative: every operand of such an
 * instruction is treated as possibly wide.
 */
private fun Instruction.touchesWidePair(register: Int): Boolean {
    val name = opcode.name
    if (!name.contains("WIDE") && !name.contains("LONG") && !name.contains("DOUBLE")) return false
    return registerOperands().any { it == register || it + 1 == register }
}

/** True when [this] writes register [register] (or, for a wide write, the pair it starts). */
private fun Instruction.writesRegister(register: Int): Boolean {
    if (!opcode.setsRegister()) return false
    val target = (this as? OneRegisterInstruction)?.registerA ?: return false
    return target == register || (opcode.setsWideRegister() && target + 1 == register)
}

/** Inserted code must be reached by fall-through: a jump to [index] would bypass it. */
private fun MutableMethod.requireFallThrough(index: Int) {
    val targeted = implementation!!.instructions.any { instruction ->
        when (instruction) {
            is BuilderOffsetInstruction -> instruction.target.location.index == index
            is BuilderSwitchPayload -> instruction.switchElements.any { it.target.location.index == index }
            else -> false
        }
    }
    check(!targeted) { "Rich text: $definingClass->$name instruction $index is a branch target" }
}
