#pragma once

// These DSP paths depend on x87 extended precision. Android x86_64 uses IEEE
// binary128 long double, so architecture checks alone select incompatible asm.
#if (defined(__x86_64__) || defined(__i386__)) \
    && (defined(__GNUC__) || defined(__clang__)) \
    && defined(__LDBL_MANT_DIG__) && (__LDBL_MANT_DIG__ == 64)
    #define LYNX_HAS_X87_EXTENDED 1
#else
    #define LYNX_HAS_X87_EXTENDED 0
#endif
