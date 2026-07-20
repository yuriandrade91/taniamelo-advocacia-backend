package com.lawfirm.law.firm.model;

/**
 * Discriminador da collection única de arquivos do cliente (client_files):
 * a aba "Documentos" e a aba "Simulações" compartilham a mesma tabela porque
 * ambas são arquivos do cliente com o mesmo ciclo de vida (upload, download,
 * edição de metadados, soft delete) - só os metadados específicos variam.
 */
public enum FileKind {
    DOCUMENT,
    SIMULATION
}
