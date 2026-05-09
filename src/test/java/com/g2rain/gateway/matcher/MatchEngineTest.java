package com.g2rain.gateway.matcher;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchEngineTest {
    @Test
    void shouldPreferMethodBucketBeforeAnyMethodBucket() {
        MatchEngine<String> engine = new MatchEngine<>();
        RuleCompiler<String> compiler = new RuleCompiler<>();

        engine.replace(compiler.compile(List.of(
            new RuleDefinition<>(1L, "ALL", "/api/orders/{id}", "all-route"),
            new RuleDefinition<>(2L, "GET", "/api/orders/{id}", "get-route")
        )));

        assertEquals("get-route", engine.matchRule(HttpMethod.GET, "/api/orders/1").orElseThrow().target());
        assertEquals("all-route", engine.matchRule(HttpMethod.POST, "/api/orders/1").orElseThrow().target());
    }

    @Test
    void shouldRespectMethodBucketsForExactRoutes() {
        MatchEngine<String> engine = new MatchEngine<>();
        RuleCompiler<String> compiler = new RuleCompiler<>();

        engine.replace(compiler.compile(List.of(
            new RuleDefinition<>(1L, "GET", "/api/profile", "profile-get"),
            new RuleDefinition<>(2L, "POST", "/api/profile", "profile-post")
        )));

        assertEquals("profile-get", engine.matchRule(HttpMethod.GET, "/api/profile").orElseThrow().target());
        assertEquals("profile-post", engine.matchRule(HttpMethod.POST, "/api/profile").orElseThrow().target());
        assertTrue(engine.matchRule(HttpMethod.DELETE, "/api/profile").isEmpty());
    }

    @Test
    void shouldSupportIncrementalUpsertAndRemove() {
        RuleCompiler<String> compiler = new RuleCompiler<>();

        RuleTable<String> table = compiler.compile(List.of(
            new RuleDefinition<>(1L, "GET", "/api/orders/{id}", "orders")
        ));

        table = compiler.upsert(table, new RuleDefinition<>(2L, "GET", "/api/users/{id}", "users"));
        MatchEngine<String> engine = new MatchEngine<>();
        engine.replace(table);
        assertEquals("users", engine.matchRule(HttpMethod.GET, "/api/users/1").orElseThrow().target());

        table = compiler.remove(table, new RuleDefinition<>(1L, "GET", "/api/orders/{id}", "orders"));
        engine.replace(table);
        assertTrue(engine.matchRule(HttpMethod.GET, "/api/orders/1").isEmpty());
        assertEquals("users", engine.matchRule(HttpMethod.GET, "/api/users/1").orElseThrow().target());
    }

    @Test
    void shouldKeepUnrelatedCacheEntriesOnExactUpdate() {
        MatchEngine<String> engine = new MatchEngine<>();
        RuleCompiler<String> compiler = new RuleCompiler<>();
        engine.replace(compiler.compile(List.of(
            new RuleDefinition<>(1L, "GET", "/api/orders", "orders")
        )));

        assertEquals("orders", engine.matchRule(HttpMethod.GET, "/api/orders").orElseThrow().target());
        assertEquals(1L, engine.cacheSize());

        engine.update(
            table -> compiler.upsert(table, new RuleDefinition<>(2L, "GET", "/api/health", "health")),
            List.of(MatchEngine.ScopeVersion.exact(RuleTable.getMask(HttpMethod.GET), "/api/health"))
        );

        assertEquals(1L, engine.cacheSize());
        assertEquals("orders", engine.matchRule(HttpMethod.GET, "/api/orders").orElseThrow().target());
    }

    /**
     * 模式 {@code /basis/{id}/user} 编译分桶在 {@code /basis}，真实请求 {@code /basis/1/user} 的初始 bucket key 为 {@code /basis/1}；
     * 依赖「分层分桶前缀回退」才能命中。
     */
    @Test
    void shouldResolveMidSegmentVariablePatternViaHierarchicalBucketRelaxation() {
        MatchEngine<String> engine = new MatchEngine<>();
        RuleCompiler<String> compiler = new RuleCompiler<>();

        engine.replace(compiler.compile(List.of(
            new RuleDefinition<>(1L, "GET", "/basis/{id}/user", "mid-user")
        )));

        assertEquals("mid-user", engine.matchRule(HttpMethod.GET, "/basis/1/user").orElseThrow().target());
    }
}
