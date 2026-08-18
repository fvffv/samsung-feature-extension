/* Link-only symbol stub. This file is never packaged. */
    .text
    .p2align 2

    .global getenv
    .type getenv, %function
getenv:
    mov x0, xzr
    ret
    .size getenv, .-getenv

    .global chdir
    .type chdir, %function
chdir:
    mov w0, #-1
    ret
    .size chdir, .-chdir

    .section .note.GNU-stack,"",%progbits
