package com.api.bizplay_chatbot.telegram.event;

import java.util.UUID;

/**
 * Published by {@code TelegramBotService} the moment a bot's Telegram link is
 * successfully persisted. The polling manager listens and spins up a poller
 * thread for the bot.
 *
 * <p>An event-based hand-off decouples the lifecycle (service → manager) from
 * the inverse dependency (manager → service.processUpdate), preventing a
 * circular bean reference.
 */
public record TelegramBotConfiguredEvent(UUID botId, String token) {}
