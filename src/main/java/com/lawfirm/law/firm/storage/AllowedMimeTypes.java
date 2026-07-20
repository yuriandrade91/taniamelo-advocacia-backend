package com.lawfirm.law.firm.storage;

import java.util.Set;

/**
 * Whitelist de mime types aceitos por upload: documentos podem ser PDF/PNG/JPEG; simulações são
 * sempre um PDF importado do Meu INSS. Centralizado aqui para não duplicar a lista em cada service.
 */
public final class AllowedMimeTypes {

    public static final Set<String> DOCUMENTS =
            Set.of("application/pdf", "image/png", "image/jpeg");

    public static final Set<String> SIMULATIONS = Set.of("application/pdf");

    private AllowedMimeTypes() {}
}
