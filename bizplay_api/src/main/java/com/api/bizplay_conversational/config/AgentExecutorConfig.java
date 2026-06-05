package com.api.bizplay_conversational.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Thread pool used to fan out conversational sub-agents (e.g. staff lookup + text analysis)
 * concurrently. Each sub-agent makes an independent LLM call, so running them in parallel
 * cuts per-turn latency to roughly the slowest single agent instead of their sum.
 */
@Configuration
public class AgentExecutorConfig {

    @Bean(name = "agentTaskExecutor")
    public Executor agentTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("agent-");
        executor.initialize();
        return executor;
    }
}
