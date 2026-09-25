package br.com.biosolar.citrus.exception;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Traduz excecoes em respostas HTTP claras e consistentes. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<ErroResponse> naoEncontrado(RecursoNaoEncontradoException e) {
        return resposta(HttpStatus.NOT_FOUND, "NAO_ENCONTRADO", e.getMessage(), List.of());
    }

    @ExceptionHandler(OperacaoNaoPermitidaException.class)
    public ResponseEntity<ErroResponse> naoPermitida(OperacaoNaoPermitidaException e) {
        return resposta(HttpStatus.FORBIDDEN, "OPERACAO_NAO_PERMITIDA", e.getMessage(), List.of());
    }

    @ExceptionHandler(ConflitoException.class)
    public ResponseEntity<ErroResponse> conflito(ConflitoException e) {
        return resposta(HttpStatus.CONFLICT, "CONFLITO", e.getMessage(), List.of());
    }

    @ExceptionHandler(DadosInvalidosException.class)
    public ResponseEntity<ErroResponse> dadosInvalidos(DadosInvalidosException e) {
        return resposta(HttpStatus.BAD_REQUEST, "REQUISICAO_INVALIDA", e.getMessage(), e.getDetalhes());
    }

    @ExceptionHandler(BancoIndisponivelException.class)
    public ResponseEntity<ErroResponse> bancoIndisponivel(BancoIndisponivelException e) {
        return resposta(HttpStatus.SERVICE_UNAVAILABLE, "BANCO_INDISPONIVEL", e.getMessage(), List.of());
    }

    @ExceptionHandler(ExportacaoException.class)
    public ResponseEntity<ErroResponse> exportacao(ExportacaoException e) {
        return resposta(e.getStatus(), e.getMotivo(), e.getMessage(), List.of());
    }

    /** Banco fora do ar durante uma consulta (ex.: relatorio): a automacao continua em memoria. */
    @ExceptionHandler({CannotCreateTransactionException.class, DataAccessResourceFailureException.class})
    public ResponseEntity<ErroResponse> bancoForaDoAr(Exception e) {
        log.warn("Banco indisponivel: {}", e.getMessage());
        return resposta(HttpStatus.SERVICE_UNAVAILABLE, "BANCO_INDISPONIVEL",
                "O banco de dados está temporariamente indisponível. A automação continua funcionando; "
                        + "tente novamente em alguns segundos.", List.of());
    }

    /** Restricao do banco violada (CHECK, FK, chave duplicada): o dado nao foi gravado. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErroResponse> integridade(DataIntegrityViolationException e) {
        log.warn("Restricao do banco violada: {}", e.getMostSpecificCause().getMessage());
        return resposta(HttpStatus.CONFLICT, "RESTRICAO_DO_BANCO",
                "Os dados violam uma regra de integridade do banco e não foram gravados.", List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErroResponse> validacao(MethodArgumentNotValidException e) {
        List<String> detalhes = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .toList();
        return resposta(HttpStatus.BAD_REQUEST, "REQUISICAO_INVALIDA", "Dados da requisição inválidos.", detalhes);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErroResponse> corpoInvalido(HttpMessageNotReadableException e) {
        return resposta(HttpStatus.BAD_REQUEST, "JSON_INVALIDO",
                "Corpo da requisição ausente ou malformado. Verifique o JSON e os valores dos campos "
                        + "(ex.: {\"talhaoId\": \"A\", \"ligado\": true}).",
                List.of());
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ErroResponse> parametroInvalido(Exception e) {
        return resposta(HttpStatus.BAD_REQUEST, "PARAMETRO_INVALIDO", e.getMessage(), List.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErroResponse> metodoNaoSuportado(HttpRequestMethodNotSupportedException e) {
        return resposta(HttpStatus.METHOD_NOT_ALLOWED, "METODO_NAO_SUPORTADO",
                "Método " + e.getMethod() + " não suportado neste endpoint.", List.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErroResponse> rotaInexistente(NoResourceFoundException e) {
        return resposta(HttpStatus.NOT_FOUND, "ROTA_INEXISTENTE", "Recurso /" + e.getResourcePath() + " não existe.",
                List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErroResponse> erroInterno(Exception e) {
        log.error("Erro inesperado", e);
        return resposta(HttpStatus.INTERNAL_SERVER_ERROR, "ERRO_INTERNO",
                "Erro interno no servidor. Consulte os logs da API.", List.of());
    }

    private static ResponseEntity<ErroResponse> resposta(HttpStatus status, String motivo, String mensagem,
                                                         List<String> detalhes) {
        return ResponseEntity.status(status).body(ErroResponse.de(status.value(), motivo, mensagem, detalhes));
    }
}