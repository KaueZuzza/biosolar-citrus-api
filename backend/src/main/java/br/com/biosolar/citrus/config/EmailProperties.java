package br.com.biosolar.citrus.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Servidor SMTP usado para enviar relatorios por e-mail (prefixo {@code biosolar.email}).
 * Os valores vem do .env (BIOSOLAR_SMTP_*); a senha nunca e enviada ao navegador.
 *
 * @param host          servidor SMTP (vazio = envio de e-mail desativado)
 * @param porta         587 (STARTTLS) ou 465 (SSL)
 * @param usuario       login no servidor SMTP
 * @param senha         senha ou "senha de app" do provedor
 * @param remetente     endereco que aparece como remetente (padrao: o usuario)
 * @param nomeRemetente nome exibido junto ao remetente
 * @param starttls      usa STARTTLS (porta 587)
 * @param ssl           usa SSL direto (porta 465)
 * @param limitePorHora protecao contra abuso: maximo de e-mails enviados por hora
 */
@ConfigurationProperties(prefix = "biosolar.email")
public record EmailProperties(
        @DefaultValue("") String host,
        @DefaultValue("587") int porta,
        @DefaultValue("") String usuario,
        @DefaultValue("") String senha,
        @DefaultValue("") String remetente,
        @DefaultValue("BioSolar Citrus") String nomeRemetente,
        @DefaultValue("true") boolean starttls,
        @DefaultValue("false") boolean ssl,
        @DefaultValue("20") int limitePorHora) {

    public boolean configurado() {
        return host != null && !host.isBlank() && !remetenteEfetivo().isBlank();
    }

    public String remetenteEfetivo() {
        if (remetente != null && !remetente.isBlank()) {
            return remetente.trim();
        }
        return usuario == null ? "" : usuario.trim();
    }
}
