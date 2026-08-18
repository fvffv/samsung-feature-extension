/*
 * Minimal JNI bridge for Android/bionic.
 *
 * Android's public android.system.Os wrapper does not expose chdir(). The
 * module sets SFE_AUDIO_ERASER_ROOT through Os.setenv(), then calls this JNI
 * method before VEKit opens the relative model path "system/etc".
 */

    .text
    .p2align 2
    .global Java_com_samsung_feature_extension_videoeditor_VideoEditorAudioEraserHook_nativeActivateModelTree
    .type Java_com_samsung_feature_extension_videoeditor_VideoEditorAudioEraserHook_nativeActivateModelTree, %function

Java_com_samsung_feature_extension_videoeditor_VideoEditorAudioEraserHook_nativeActivateModelTree:
    stp x29, x30, [sp, #-16]!
    mov x29, sp

    adrp x0, .Lroot_environment_name
    add x0, x0, :lo12:.Lroot_environment_name
    bl getenv
    cbz x0, .Lfailed

    bl chdir
    b .Lreturn

.Lfailed:
    mov w0, #-1

.Lreturn:
    ldp x29, x30, [sp], #16
    ret

    .size Java_com_samsung_feature_extension_videoeditor_VideoEditorAudioEraserHook_nativeActivateModelTree, .-Java_com_samsung_feature_extension_videoeditor_VideoEditorAudioEraserHook_nativeActivateModelTree

    .section .rodata
    .p2align 2
.Lroot_environment_name:
    .asciz "SFE_AUDIO_ERASER_ROOT"

    .section .note.GNU-stack,"",%progbits
