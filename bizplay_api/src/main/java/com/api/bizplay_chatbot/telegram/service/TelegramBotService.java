package com.api.bizplay_chatbot.telegram.service;

import com.api.bizplay_chatbot.common.exception.BusinessException;
import com.api.bizplay_chatbot.domain.entity.Bot;
import com.api.bizplay_chatbot.domain.entity.BotRecommendedQuestion;
import com.api.bizplay_chatbot.domain.entity.TelegramChat;
import com.api.bizplay_chatbot.domain.repository.BotRepository;
import com.api.bizplay_chatbot.domain.repository.TelegramChatRepository;
import com.api.bizplay_chatbot.rag.chat.dto.ChatRequest;
import com.api.bizplay_chatbot.rag.chat.dto.ChatResponse;
import com.api.bizplay_chatbot.rag.chat.service.ChatService;
import com.api.bizplay_chatbot.telegram.client.TelegramApiClient;
import com.api.bizplay_chatbot.telegram.dto.CallbackQuery;
import com.api.bizplay_chatbot.telegram.dto.InlineKeyboardButton;
import com.api.bizplay_chatbot.telegram.dto.InlineKeyboardMarkup;
import com.api.bizplay_chatbot.telegram.dto.TelegramMessage;
import com.api.bizplay_chatbot.telegram.dto.TelegramStatusResponse;
import com.api.bizplay_chatbot.telegram.dto.TelegramUser;
import com.api.bizplay_chatbot.telegram.dto.Update;
import com.api.bizplay_chatbot.telegram.event.TelegramBotConfiguredEvent;
import com.api.bizplay_chatbot.telegram.event.TelegramBotUnconfiguredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Telegram channel adapter — owns the per-bot Telegram lifecycle (configure,
 * unconfigure) and the inbound-update processing pipeline.
 *
 * Inbound flow (long polling, no inbound webhook):
 *   TelegramPollingManager's per-bot daemon thread blocks on getUpdates
 *            → handed update to {@link #processUpdate(UUID, Update)} synchronously
 *            → ChatService.chat(...) → reply via {@link TelegramApiClient}
 *            → poller advances offset and loops
 *
 * Configure flow:
 *   1. validate token via getMe
 *   2. drop any pre-existing webhook (Telegram returns 409 on getUpdates if a
 *      webhook is still active for the token, even from a previous deployment)
 *   3. discard any backlog Telegram has queued so the first poll cycle returns
 *      only fresh messages — equivalent to webhook's drop_pending_updates
 *   4. persist token + username + starting offset
 *   5. publish TelegramBotConfiguredEvent — the polling manager listens and
 *      spins up the bot's poller thread
 *
 * Group chats are explicitly out of scope: anything other than
 * {@code chat.type == "private"} is bounced with a one-line notice.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramBotService {

    /** Telegram caps a single sendMessage at 4096 chars; 4000 leaves margin
     *  for HTML escaping bloat. */
    private static final int MESSAGE_SPLIT_THRESHOLD = 4000;
    /** Telegram caps callback_data at 64 bytes — recommended questions longer
     *  than this need a different transport (the button can't carry the full
     *  question). We truncate the button label and store an indexed key in
     *  callback_data instead — see {@link #buildRecommendedQuestionsKeyboard}. */
    private static final int CALLBACK_DATA_MAX_BYTES = 64;
    private static final String CALLBACK_QUESTION_PREFIX = "rq:";

    private final BotRepository botRepository;
    private final TelegramChatRepository telegramChatRepository;
    private final TelegramApiClient telegramApiClient;
    private final ChatService chatService;
    private final ApplicationEventPublisher eventPublisher;

    // ─── Configure / status ──────────────────────────────────────────────

    @Transactional
    public TelegramStatusResponse configure(UUID botId, String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BusinessException("token is required", HttpStatus.BAD_REQUEST);
        }
        String token = rawToken.trim();

        Bot bot = botRepository.findById(botId)
                .orElseThrow(() -> new BusinessException("Bot not found: " + botId, HttpStatus.NOT_FOUND));
        // Disabled bots are intentionally allowed to be linked to Telegram —
        // newly-created bots start disabled, and operators need to wire up
        // Telegram before flipping the bot on. processUpdate still
        // short-circuits with a polite "unavailable" reply for disabled bots,
        // so no chat traffic actually flows until the operator enables it.

        // Refuse to claim a token already linked to a different bot in this
        // system — two pollers on the same token would race and Telegram
        // would 409 one of them out.
        Optional<Bot> existing = botRepository.findByTelegramBotToken(token);
        if (existing.isPresent() && !existing.get().getId().equals(botId)) {
            throw new BusinessException(
                    "This Telegram token is already linked to another bot",
                    HttpStatus.CONFLICT);
        }

        // 1) Validate token via getMe — 4xx errors here surface to the caller as 400.
        TelegramUser me = telegramApiClient.getMe(token);
        if (!me.isBot()) {
            throw new BusinessException(
                    "Token does not belong to a Telegram bot account",
                    HttpStatus.BAD_REQUEST);
        }

        // 2) Ensure clean state: drop any webhook the token may still have
        //    registered (from an earlier deployment or another system).
        //    Telegram rejects getUpdates with 409 while a webhook is set, so
        //    this is mandatory, not defensive.
        try {
            telegramApiClient.deleteWebhook(token);
        } catch (RuntimeException e) {
            log.warn("deleteWebhook during configure failed for botId={} (continuing): {}",
                    botId, e.getMessage());
        }

        // 3) Drop any backlog Telegram has queued for this bot from before it
        //    was linked, so we don't replay stale messages on first poll.
        //    getUpdates with offset=-1, timeout=0 returns at most one update
        //    (the latest queued); we then store its id+1 as our starting
        //    offset, effectively discarding everything Telegram had.
        Long startingOffset = null;
        try {
            List<Update> backlog = telegramApiClient.getUpdates(token, -1L, 0, null);
            if (!backlog.isEmpty()) {
                startingOffset = backlog.get(0).getUpdateId() + 1L;
            }
        } catch (RuntimeException e) {
            log.warn("Backlog drop during configure failed for botId={} (continuing with null offset): {}",
                    botId, e.getMessage());
        }

        // 4) Persist.
        bot.setTelegramBotToken(token);
        bot.setTelegramBotUsername(me.getUsername());
        bot.setTelegramLastOffset(startingOffset);
        bot.setTelegramConfiguredAt(LocalDateTime.now());
        botRepository.save(bot);

        log.info("Telegram configured (long polling): botId={}, tg_username=@{}, startingOffset={}",
                botId, me.getUsername(), startingOffset);

        // 5) Hand off to the polling manager. The event is dispatched AFTER
        //    the transaction commits (Spring's default behaviour for
        //    ApplicationEventPublisher inside @Transactional) so the manager
        //    sees the persisted token when it loads the bot.
        eventPublisher.publishEvent(new TelegramBotConfiguredEvent(botId, token));

        return TelegramStatusResponse.builder()
                .telegramConfigured(true)
                .botUsername(me.getUsername())
                .configuredAt(bot.getTelegramConfiguredAt())
                .build();
    }

    @Transactional(readOnly = true)
    public TelegramStatusResponse status(UUID botId) {
        Bot bot = botRepository.findById(botId)
                .orElseThrow(() -> new BusinessException("Bot not found: " + botId, HttpStatus.NOT_FOUND));
        boolean configured = bot.getTelegramBotToken() != null;
        return TelegramStatusResponse.builder()
                .telegramConfigured(configured)
                .botUsername(bot.getTelegramBotUsername())
                .configuredAt(bot.getTelegramConfiguredAt())
                .build();
    }

    @Transactional
    public TelegramStatusResponse unconfigure(UUID botId) {
        Bot bot = botRepository.findById(botId)
                .orElseThrow(() -> new BusinessException("Bot not found: " + botId, HttpStatus.NOT_FOUND));
        String token = bot.getTelegramBotToken();

        // Tell the polling manager to stop the poller first. The manager
        // interrupts the thread and waits for it to exit — by the time this
        // call returns, no more updates will be processed for this bot.
        eventPublisher.publishEvent(new TelegramBotUnconfiguredEvent(botId));

        if (token != null) {
            // Best-effort cleanup on Telegram's side. If a webhook somehow
            // got re-registered out of band, this drops it; if not, it's a
            // no-op. Failures don't block local cleanup.
            try {
                telegramApiClient.deleteWebhook(token);
            } catch (RuntimeException e) {
                log.warn("deleteWebhook failed for botId={} (continuing): {}",
                        botId, e.getMessage());
            }
        }
        bot.setTelegramBotToken(null);
        bot.setTelegramBotUsername(null);
        bot.setTelegramLastOffset(null);
        bot.setTelegramConfiguredAt(null);
        botRepository.save(bot);
        log.info("Telegram unconfigured: botId={}", botId);
        return TelegramStatusResponse.builder()
                .telegramConfigured(false)
                .build();
    }

    // ─── Inbound update processing ───────────────────────────────────────

    /**
     * Process one Telegram update. Called synchronously from the polling
     * thread in {@link TelegramPollingManager} — per-bot serial execution so a
     * single user can't have their next reply overtake the previous one. No
     * {@code @Async} now: long polling provides backpressure naturally (the
     * polling thread simply doesn't fetch the next batch until this returns).
     *
     * Top-level try/catch isolates the update so a failed message never kills
     * the polling loop. We still try to send a polite error reply to the user
     * so they know the bot isn't ignoring them; if even that fails the warning
     * is logged and processing continues.
     */
    public void processUpdate(UUID botId, Update update) {
        try {
            doProcessUpdate(botId, update);
        } catch (RuntimeException ex) {
            log.error("processUpdate failed for botId={}: {}", botId, ex.getMessage(), ex);
            tryReplyError(botId, update);
        }
    }

    private void doProcessUpdate(UUID botId, Update update) {
        // Eager-fetch recommendedQuestions because /start and the rq:N
        // callback path read it after the Hibernate session has closed —
        // processUpdate is @Async without @Transactional, deliberately, so
        // we don't hold a DB connection during the LLM call.
        Bot bot = botRepository.findWithRecommendedQuestionsById(botId).orElse(null);
        if (bot == null || bot.getTelegramBotToken() == null) {
            log.warn("Update for unknown or unconfigured bot: botId={}", botId);
            return;
        }

        TelegramMessage incoming = update.getMessage();
        CallbackQuery cb = update.getCallbackQuery();
        if (incoming == null && cb != null) {
            incoming = cb.getMessage();
        }
        if (incoming == null || incoming.getChat() == null) {
            log.debug("Update has no message/chat we can act on, dropping");
            return;
        }

        com.api.bizplay_chatbot.telegram.dto.TelegramChat chat = incoming.getChat();
        long chatId = chat.getId();

        // V1 supports private 1-on-1 chats only. Group support adds @mention
        // parsing + per-user history, which we're deferring.
        if (!"private".equals(chat.getType())) {
            sendPlain(bot, chatId, "This bot is only available in private chats.", null);
            return;
        }

        String text = (cb != null && cb.getData() != null) ? cb.getData() : incoming.getText();
        if (text == null || text.isBlank()) {
            return;
        }

        // Resolve the actual question payload. Buttons we built ourselves carry
        // a "rq:<index>" callback_data — look up the recommended question text
        // by index. Plain text (typed messages or callback data we didn't
        // shorten) is forwarded as-is.
        String query = text;
        if (cb != null && text.startsWith(CALLBACK_QUESTION_PREFIX)) {
            String resolved = resolveRecommendedQuestion(bot, text);
            if (resolved != null) query = resolved;
        }

        // Bot replies are intentionally NOT linked to the user's message via
        // reply_to_message_id — Telegram would render that as a quoted preview
        // ("user said X") above every answer, which is visual noise in a
        // request/response chat where the conversation order already conveys
        // context. Pass null to all helpers so the bot just sends standalone
        // messages.

        // Slash commands take priority over the chat pipeline.
        if (text.startsWith("/start")) {
            handleStart(bot, chatId, null);
            return;
        }
        if (text.startsWith("/help")) {
            sendPlain(bot, chatId,
                    "Send me a question and I'll answer using my knowledge base.\n\n" +
                            "/start — show recommended questions\n" +
                            "/reset — start a new conversation\n" +
                            "/help — show this message",
                    null);
            return;
        }
        if (text.startsWith("/reset")) {
            handleReset(bot, chatId, null);
            return;
        }

        if (bot.isDisabled()) {
            sendPlain(bot, chatId, "This bot is currently unavailable. Please try again later.",
                    null);
            return;
        }

        // Surface a "typing…" indicator — works whether or not the LLM call
        // succeeds, and is fire-and-forget inside the client.
        telegramApiClient.sendChatAction(bot.getTelegramBotToken(), chatId, "typing");

        Optional<TelegramChat> mapping = telegramChatRepository.findByBotIdAndChatId(botId, chatId);
        UUID existingSessionId = mapping.map(TelegramChat::getSessionId).orElse(null);

        ChatRequest chatRequest = new ChatRequest(botId, query, existingSessionId, "telegram");
        ChatResponse chatResponse = chatService.chat(chatRequest);

        // Persist / refresh the (bot, chat_id) → session_id mapping. The
        // session id we got back is the newly-created one when existingSessionId
        // was null, or the unchanged one when we passed in an existing id.
        upsertMapping(botId, chatId, chatResponse.getSessionId(), incoming);

        sendReply(bot, chatId, chatResponse.getAnswer(), null);
    }

    private void handleStart(Bot bot, long chatId, Long replyToId) {
        String greeting = (bot.getDescription() != null && !bot.getDescription().isBlank())
                ? bot.getDescription()
                : "Hi! I'm " + bot.getName() + ". Ask me anything.";
        InlineKeyboardMarkup keyboard = buildRecommendedQuestionsKeyboard(bot);
        sendPlain(bot, chatId, greeting, replyToId, keyboard);
    }

    private void handleReset(Bot bot, long chatId, Long replyToId) {
        // Drop the mapping → next message starts a new ChatSession naturally
        // via ChatService.chat(sessionId=null, …).
        int removed = telegramChatRepository.deleteByBotIdAndChatId(bot.getId(), chatId);
        String text = removed > 0 ? "Conversation reset. Send a new message to start fresh."
                                   : "No active conversation to reset.";
        sendPlain(bot, chatId, text, replyToId);
    }

    /** Either insert a new mapping or refresh the timestamp / session of an
     *  existing one. Two-step instead of upsert because Spring Data JPA
     *  doesn't ship a portable upsert. */
    private void upsertMapping(UUID botId, long chatId, UUID sessionId, TelegramMessage incoming) {
        Optional<TelegramChat> existing = telegramChatRepository.findByBotIdAndChatId(botId, chatId);
        TelegramChat row = existing.orElseGet(() -> {
            TelegramChat fresh = new TelegramChat();
            fresh.setId(UUID.randomUUID());
            fresh.setBotId(botId);
            fresh.setChatId(chatId);
            fresh.setCreatedAt(LocalDateTime.now());
            return fresh;
        });
        row.setSessionId(sessionId);
        row.setLastMessageAt(LocalDateTime.now());
        if (incoming.getFrom() != null) {
            row.setTgUsername(incoming.getFrom().getUsername());
            row.setTgFirstName(incoming.getFrom().getFirstName());
        }
        telegramChatRepository.save(row);
    }

    // ─── Reply rendering / sending ───────────────────────────────────────

    private void sendReply(Bot bot, long chatId, String answer, Long replyToId) {
        if (answer == null || answer.isBlank()) {
            answer = "I couldn't generate a response. Please try again.";
        }
        String html = markdownToHtml(answer);
        // Telegram caps a single message at 4096 chars; we split at the last
        // newline below the threshold so paragraph boundaries are preserved.
        for (String chunk : splitForTelegram(html)) {
            telegramApiClient.sendMessage(bot.getTelegramBotToken(), chatId, chunk,
                    "HTML", replyToId, null);
            // Only first chunk should "reply to" the user message — subsequent
            // chunks are continuation messages.
            replyToId = null;
        }
    }

    private void sendPlain(Bot bot, long chatId, String text, Long replyToId) {
        sendPlain(bot, chatId, text, replyToId, null);
    }

    private void sendPlain(Bot bot, long chatId, String text, Long replyToId,
                           InlineKeyboardMarkup keyboard) {
        telegramApiClient.sendMessage(bot.getTelegramBotToken(), chatId,
                escapeHtml(text), "HTML", replyToId, keyboard);
    }

    /** Best-effort error notification when processUpdate explodes after the
     *  webhook already returned 200. */
    private void tryReplyError(UUID botId, Update update) {
        try {
            Bot bot = botRepository.findById(botId).orElse(null);
            if (bot == null || bot.getTelegramBotToken() == null) return;
            Long chatId = extractChatId(update);
            if (chatId == null) return;
            telegramApiClient.sendMessage(bot.getTelegramBotToken(), chatId,
                    "Sorry, I hit an internal error. Please try again in a moment.",
                    null, null, null);
        } catch (RuntimeException nested) {
            log.warn("Failed to deliver fallback error message: {}", nested.getMessage());
        }
    }

    private static Long extractChatId(Update update) {
        TelegramMessage m = update.getMessage();
        if (m != null && m.getChat() != null) return m.getChat().getId();
        if (update.getCallbackQuery() != null && update.getCallbackQuery().getMessage() != null
                && update.getCallbackQuery().getMessage().getChat() != null) {
            return update.getCallbackQuery().getMessage().getChat().getId();
        }
        return null;
    }

    /** Map "rq:<index>" callback_data back to the actual question. */
    private static String resolveRecommendedQuestion(Bot bot, String callbackData) {
        try {
            int index = Integer.parseInt(callbackData.substring(CALLBACK_QUESTION_PREFIX.length()));
            List<BotRecommendedQuestion> qs = bot.getRecommendedQuestions();
            if (index >= 0 && index < qs.size()) return qs.get(index).getQuestion();
        } catch (NumberFormatException ignored) {
            // fall through
        }
        return null;
    }

    private static InlineKeyboardMarkup buildRecommendedQuestionsKeyboard(Bot bot) {
        List<BotRecommendedQuestion> qs = bot.getRecommendedQuestions();
        if (qs == null || qs.isEmpty()) return null;
        // One button per row keeps long question labels readable on phones.
        // callback_data uses an index reference so the 64-byte limit can never
        // be hit by a long question.
        List<List<InlineKeyboardButton>> rows = new ArrayList<>(qs.size());
        for (int i = 0; i < qs.size(); i++) {
            String label = qs.get(i).getQuestion();
            String callbackData = CALLBACK_QUESTION_PREFIX + i;
            // Defensive: callback_data should always fit, but guard in case
            // the prefix scheme is ever changed.
            if (callbackData.getBytes().length > CALLBACK_DATA_MAX_BYTES) continue;
            rows.add(List.of(new InlineKeyboardButton(label, callbackData)));
        }
        return rows.isEmpty() ? null : new InlineKeyboardMarkup(rows);
    }

    // ─── Formatting helpers ──────────────────────────────────────────────

    /** Convert the small subset of markdown LLMs typically emit (bold, code,
     *  bullets, headers) to HTML Telegram understands, then escape any raw
     *  HTML entities that survived. Markdown-V2 has 18 reserved characters
     *  needing backslash escaping; HTML only needs &lt; &gt; &amp; — far
     *  fewer ways to break a message. */
    static String markdownToHtml(String src) {
        if (src == null) return "";
        StringBuilder out = new StringBuilder(src.length() + 16);
        String[] lines = src.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            // Drop "# " / "## " / etc. markdown header marks; Telegram has no
            // header concept and we don't want a leading "# " in the rendering.
            line = line.replaceFirst("^\\s*#{1,6}\\s+", "");
            // Bullet conversion: "- foo" / "* foo" → "• foo".
            line = line.replaceFirst("^(\\s*)[-*]\\s+", "$1• ");
            line = convertInline(line);
            out.append(line);
            if (i < lines.length - 1) out.append('\n');
        }
        return out.toString();
    }

    private static String convertInline(String line) {
        // Order matters: handle code (which should NOT escape its content) first.
        StringBuilder out = new StringBuilder(line.length() + 8);
        int i = 0;
        while (i < line.length()) {
            char c = line.charAt(i);
            if (c == '`') {
                int end = line.indexOf('`', i + 1);
                if (end > i) {
                    out.append("<code>")
                       .append(escapeHtml(line.substring(i + 1, end)))
                       .append("</code>");
                    i = end + 1;
                    continue;
                }
            }
            if (c == '*' && i + 1 < line.length() && line.charAt(i + 1) == '*') {
                int end = line.indexOf("**", i + 2);
                if (end > i + 1) {
                    out.append("<b>")
                       .append(escapeHtml(line.substring(i + 2, end)))
                       .append("</b>");
                    i = end + 2;
                    continue;
                }
            }
            // Escape one char at a time — simpler than trying to track HTML-safe
            // runs and stitch them together.
            switch (c) {
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '&' -> out.append("&amp;");
                default -> out.append(c);
            }
            i++;
        }
        return out.toString();
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Split at the last newline below the threshold. Falls back to a hard
     *  split when a single line exceeds the threshold. */
    static List<String> splitForTelegram(String text) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int remaining = text.length() - start;
            if (remaining <= MESSAGE_SPLIT_THRESHOLD) {
                parts.add(text.substring(start));
                break;
            }
            int end = start + MESSAGE_SPLIT_THRESHOLD;
            int lastNewline = text.lastIndexOf('\n', end);
            // Only honor the newline if it's reasonably close to the cap (>50%
            // of the budget consumed) — otherwise we'd emit a tiny first chunk.
            int splitAt = (lastNewline > start + MESSAGE_SPLIT_THRESHOLD / 2)
                    ? lastNewline + 1
                    : end;
            parts.add(text.substring(start, splitAt));
            start = splitAt;
        }
        return parts;
    }
}
