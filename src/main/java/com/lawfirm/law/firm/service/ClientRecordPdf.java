package com.lawfirm.law.firm.service;

/** O PDF pronto da ficha e o nome sugerido para o arquivo baixado. */
public record ClientRecordPdf(String filename, byte[] content) {}
