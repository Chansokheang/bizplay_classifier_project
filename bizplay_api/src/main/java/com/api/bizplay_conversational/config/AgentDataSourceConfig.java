package com.api.bizplay_conversational.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Wires two datasources so the conversational DATABASE-LOOKUP sub-agent runs its
 * LLM-generated SQL under a locked-down, read-only database role while the rest of
 * the application keeps full read-write access.
 *
 * <p>Because the app previously relied on Spring Boot's single auto-configured
 * DataSource, defining a second DataSource bean here forces us to declare the
 * primary one explicitly too (otherwise auto-configuration backs off and JPA /
 * MyBatis lose their connection). The primary is marked {@link Primary} so every
 * repository, mapper, JPA context, Spring Session, and vector store continues to
 * use the read-write {@code spring.datasource.*} connection exactly as before.
 *
 * <p><b>Do not</b> point application persistence at {@code agentReadOnlyDataSource}
 * or {@code agentJdbcTemplate} -- that connection authenticates as
 * {@code bizplay_agent_ro}, which Postgres runs in read-only mode. It exists for
 * one consumer only: {@code DatabaseLookupAgentServiceImple}.
 */
@Configuration
public class AgentDataSourceConfig {

    // --- Primary read-write datasource (unchanged behaviour, now explicit) -------

    @Primary
    @Bean
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties primaryDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Primary
    @Bean
    @ConfigurationProperties("spring.datasource.hikari")
    public DataSource dataSource(
            @Qualifier("primaryDataSourceProperties") DataSourceProperties primaryDataSourceProperties) {
        return primaryDataSourceProperties.initializeDataSourceBuilder().build();
    }

    /**
     * Explicit primary read-write JdbcTemplate.
     *
     * <p>Spring Boot's auto-configured JdbcTemplate is {@code @ConditionalOnMissingBean};
     * declaring {@link #agentJdbcTemplate} below would make it back off, leaving the
     * read-only template as the only candidate for every by-type {@code JdbcTemplate}
     * injection (BotService, DocumentService, ChatService, the PGVector store, ...) and
     * breaking their writes. Re-declaring the primary here keeps by-type injections on
     * the read-write connection; only the agent opts into the read-only one via qualifier.
     */
    @Primary
    @Bean
    public JdbcTemplate jdbcTemplate(@Qualifier("dataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    // --- Read-only datasource used ONLY by the database-lookup sub-agent ---------

    @Bean
    @ConfigurationProperties("app.conversational.agent-datasource")
    public DataSourceProperties agentDataSourceProperties() {
        return new DataSourceProperties();
    }

    /**
     * Connection pool for the read-only agent role. {@code read-only=true} is set
     * on the Hikari pool as an extra JDBC-level guard on top of the role's
     * {@code default_transaction_read_only} setting. A small pool keeps the agent
     * from starving the main pool.
     */
    @Bean(name = "agentReadOnlyDataSource")
    @ConfigurationProperties("app.conversational.agent-datasource.hikari")
    public DataSource agentReadOnlyDataSource(
            @Qualifier("agentDataSourceProperties") DataSourceProperties agentDataSourceProperties) {
        return agentDataSourceProperties.initializeDataSourceBuilder().build();
    }

    /**
     * The only JdbcTemplate the database-lookup agent is allowed to use. Row and
     * query-timeout caps are belt-and-braces alongside the role's
     * {@code statement_timeout} and the agent's own {@code LIMIT} enforcement.
     */
    @Bean(name = "agentJdbcTemplate")
    public JdbcTemplate agentJdbcTemplate(
            @Qualifier("agentReadOnlyDataSource") DataSource agentReadOnlyDataSource) {
        JdbcTemplate template = new JdbcTemplate(agentReadOnlyDataSource);
        template.setMaxRows(50);
        template.setQueryTimeout(3);
        return template;
    }
}
