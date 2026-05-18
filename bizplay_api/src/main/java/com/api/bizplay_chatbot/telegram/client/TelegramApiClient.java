package com.api.bizplay_chatbot.telegram.client;

import com.api.bizplay_chatbot.telegram.dto.InlineKeyboardMarkup;
import com.api.bizplay_chatbot.telegram.dto.TelegramApiResponse;
import com.api.bizplay_chatbot.telegram.dto.TelegramMessage;
import com.api.bizplay_chatbot.telegram.dto.TelegramUser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stateless wrapper around the Telegram Bot API. Token is passed per-call so a
 * single client instance can serve every configured bot — there's no per-bot
 * state to keep here.
 *
 * Each method translates a {@code ok:false} envelope or a transport failure
 * into {@link TelegramApiException} so callers don't need to inspect the
 * envelope themselves.
 */
@Slf4j
@Component
public class TelegramApiClient {

    private static final TypeReference<TelegramApiResponse<TelegramUser>> USER_RESP =
            new TypeReference<>() {};
    private static final TypeReference<TelegramApiResponse<Boolean>> BOOL_RESP =
            new TypeReference<>() {};
    private static final TypeReference<TelegramApiResponse<TelegramMessage>> MESSAGE_RESP =
            new TypeReference<>() {};
    private static final TypeReference<TelegramApiResponse<List<com.api.bizplay_chatbot.telegram.dto.Update>>> UPDATES_RESP =
            new TypeReference<>() {};

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public TelegramApiClient(
            @Qualifier("telegramRestClient") RestClient restClient,
            ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    /** Verify a token and fetch the bot's own User profile (id + username).
     *  Used during {@code configure(...)} to reject invalid tokens with 400
     *  before persisting anything. */
    public TelegramUser getMe(String token) {
        TelegramApiResponse<TelegramUser> resp = call(token, "getMe", null, USER_RESP);
        return requireResult(resp, "getMe");
    }

    /** Long-poll for new Telegram updates. Telegram holds the connection open
     *  until either an update arrives or {@code timeoutSeconds} elapses,
     *  whichever comes first. Pass the highest-seen {@code update_id + 1}
     *  as {@code offset} so Telegram knows that everything below has been
     *  acknowledged and drops it from its queue. Pass null on first call to
     *  fetch whatever Telegram has queued (24h retention).
     *
     *  <p>{@code allowedUpdates} narrows the update kinds — we want only
     *  "message" and "callback_query"; other kinds (edited_message,
     *  channel_post, …) are intentionally dropped at the source. */
    public List<com.api.bizplay_chatbot.telegram.dto.Update> getUpdates(
            String token, Long offset, int timeoutSeconds, List<String> allowedUpdates) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (offset != null) body.put("offset", offset);
        body.put("timeout", timeoutSeconds);
        if (allowedUpdates != null && !allowedUpdates.isEmpty()) {
            body.put("allowed_updates", allowedUpdates);
        }
        TelegramApiResponse<List<com.api.bizplay_chatbot.telegram.dto.Update>> resp =
                call(token, "getUpdates", body, UPDATES_RESP);
        List<com.api.bizplay_chatbot.telegram.dto.Update> result = requireResult(resp, "getUpdates");
        return result == null ? List.of() : result;
    }

    /** Best-effort webhook removal — called at configure time to ensure a
     *  clean slate for long polling (Telegram returns 409 on getUpdates if
     *  a webhook is still registered), and at unconfigure as defense. Caller
     *  MUST tolerate failure (token may already be invalidated server-side). */
    public void deleteWebhook(String token) {
        Map<String, Object> body = Map.of("drop_pending_updates", true);
        TelegramApiResponse<Boolean> resp = call(token, "deleteWebhook", body, BOOL_RESP);
        requireResult(resp, "deleteWebhook");
    }

    /** Send a text message to a Telegram chat. Returns the persisted Message
     *  (mostly useful for the message_id, e.g. for follow-up edits). */
    public TelegramMessage sendMessage(String token, long chatId, String text,
                                       String parseMode, Long replyToMessageId,
                                       InlineKeyboardMarkup keyboard) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("chat_id", chatId);
        body.put("text", text);
        if (parseMode != null) body.put("parse_mode", parseMode);
        if (replyToMessageId != null) {
            body.put("reply_parameters", Map.of("message_id", replyToMessageId));
        }
        if (keyboard != null) body.put("reply_markup", keyboard);
        TelegramApiResponse<TelegramMessage> resp = call(token, "sendMessage", body, MESSAGE_RESP);
        return requireResult(resp, "sendMessage");
    }

    /** Tell Telegram to display a status indicator (e.g. "typing…") in the
     *  chat. Fire-and-forget; failures are logged but do not propagate. */
    public void sendChatAction(String token, long chatId, String action) {
        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("action", action);
        try {
            call(token, "sendChatAction", body, BOOL_RESP);
        } catch (RuntimeException e) {
            log.debug("sendChatAction failed (non-fatal): {}", e.getMessage());
        }
    }

    // ─── Internals ────────────────────────────────────────────────────────

    private <T> TelegramApiResponse<T> call(String token, String method, Object body,
                                            TypeReference<TelegramApiResponse<T>> typeRef) {
        String path = "/bot" + token + "/" + method;
        try {
            // Only attach Content-Type: application/json when we actually have
            // a body. Telegram rejects "POST + Content-Type: application/json
            // + Content-Length: 0" as malformed JSON for parameter-less
            // methods like getMe with HTTP 400.
            RestClient.RequestBodySpec spec = restClient.post().uri(path);
            if (body != null) {
                spec.contentType(MediaType.APPLICATION_JSON).body(body);
            }
            String json = spec.retrieve().body(String.class);
            return readResponse(json, typeRef);
        } catch (RestClientResponseException ex) {
            // Telegram returns 4xx/5xx with the standard ok:false envelope, so
            // try to surface its description rather than the raw HTTP status.
            String body0 = ex.getResponseBodyAsString();
            try {
                TelegramApiResponse<T> resp = readResponse(body0, typeRef);
                throw mapEnvelopeError(method, resp);
            } catch (TelegramApiException te) {
                throw te;
            } catch (Exception ignored) {
                throw new TelegramApiException(
                        "Telegram " + method + " failed: HTTP " + ex.getStatusCode());
            }
        } catch (ResourceAccessException ex) {
            throw new TelegramApiException(
                    "Telegram " + method + " unreachable: " + ex.getMessage());
        }
    }

    private <T> TelegramApiResponse<T> readResponse(String json,
                                                    TypeReference<TelegramApiResponse<T>> typeRef) {
        if (json == null || json.isEmpty()) {
            throw new TelegramApiException("Telegram returned empty response body");
        }
        try {
            return objectMapper.readValue(json, typeRef);
        } catch (Exception e) {
            throw new TelegramApiException("Failed to parse Telegram response: " + e.getMessage());
        }
    }

    private static <T> T requireResult(TelegramApiResponse<T> resp, String method) {
        if (resp == null || !resp.isOk()) {
            throw mapEnvelopeError(method, resp);
        }
        return resp.getResult();
    }

    private static TelegramApiException mapEnvelopeError(String method, TelegramApiResponse<?> resp) {
        String desc = resp == null ? "no response body" : resp.getDescription();
        Integer code = resp == null ? null : resp.getErrorCode();
        // Map invalid-token (401) and bad-request (400) to 400 so the configure
        // endpoint can surface them as user errors rather than 502s.
        HttpStatus status = (code != null && (code == 400 || code == 401 || code == 404))
                ? HttpStatus.BAD_REQUEST
                : HttpStatus.BAD_GATEWAY;
        return new TelegramApiException(
                "Telegram " + method + " failed: " + (desc != null ? desc : "ok=false") +
                        (code != null ? " (code " + code + ")" : ""),
                status);
    }
}
