package io.github.martindavinci.lunaris.scanner.model

enum class CodeFormat(val is2d: Boolean) {
    QR_CODE(true),
    AZTEC(true),
    DATA_MATRIX(true),
    PDF_417(true),
    MAXICODE(true),
    EAN_13(false),
    EAN_8(false),
    UPC_A(false),
    UPC_E(false),
    CODE_128(false),
    CODE_39(false),
    CODE_93(false),
    CODABAR(false),
    ITF(false),
    RSS_14(false),
    RSS_EXPANDED(false),
}
