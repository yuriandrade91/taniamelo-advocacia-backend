package com.lawfirm.law.firm.service;

import java.util.stream.Collectors;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Entities;
import org.jsoup.safety.Safelist;

/**
 * O HTML do editor rico (o relato da entrevista) pronto para ir para a ficha.
 *
 * <p>Passam só as marcas que a barra do editor produz, sem atributo nenhum - a mesma lista do
 * {@code safeRichText} do frontend. O texto pode ter vindo de fora do editor (colado, ou gravado
 * por outro cliente da API), e um {@code <img src>} levado ao PDF seria uma requisição saindo do
 * servidor. Texto sem marca nenhuma, gravado antes do editor, vira um parágrafo por linha.
 */
final class RichText {

    private static final Safelist EDITOR_TAGS =
            Safelist.none()
                    .addTags(
                            "p",
                            "br",
                            "strong",
                            "b",
                            "em",
                            "i",
                            "u",
                            "s",
                            "ul",
                            "ol",
                            "li",
                            "blockquote");

    private RichText() {}

    /** O {@code <body>} com o conteúdo limpo; serializa como XHTML bem formado. */
    static Element body(String content) {
        String html = content == null ? "" : content;
        if (!html.contains("<")) {
            html =
                    html.lines()
                            .map(line -> "<p>" + Entities.escape(line) + "</p>")
                            .collect(Collectors.joining());
        }
        Document document = Jsoup.parseBodyFragment(Jsoup.clean(html, EDITOR_TAGS));
        document.outputSettings()
                .syntax(Document.OutputSettings.Syntax.xml)
                .escapeMode(Entities.EscapeMode.xhtml)
                .prettyPrint(false);
        return document.body();
    }
}
