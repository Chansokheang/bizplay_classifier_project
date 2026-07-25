package com.api.bizplay_conversational.service.dateContextService;

/**
 * Deterministic date TOOL for the LLM agents. Models do not know what day it is, so relative
 * expressions ("next Tuesday", "tomorrow", "in two weeks") would be guessed. This tool computes an
 * authoritative calendar block (today + the coming weeks, each date with its weekday) that is
 * injected into extraction prompts, turning relative-date resolution into a table LOOKUP instead of
 * model arithmetic.
 */
public interface DateContextService {

    /** A compact, prompt-ready calendar context anchored on today (server timezone, Asia/Seoul). */
    String buildContext();
}
