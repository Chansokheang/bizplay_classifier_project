package com.api.bizplay_chatbot.telegram.event;

import java.util.UUID;

/**
 * Published by {@code TelegramBotService} when a bot's Telegram link is being
 * removed (either by explicit {@code DELETE /chatbot/api/v1/bots/{id}/telegram} or
 * because the bot itself is being deleted). The polling manager listens and
 * stops the bot's poller thread.
 *
 * <p>Carries the bot id only — the token has already been cleared from the
 * row by the time this fires, so the manager doesn't need it for anything
 * other than identifying which thread to interrupt.
 */
public record TelegramBotUnconfiguredEvent(UUID botId) {}
