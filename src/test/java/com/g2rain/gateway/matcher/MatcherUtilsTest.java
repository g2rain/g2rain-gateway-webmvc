package com.g2rain.gateway.matcher;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MatcherUtilsTest {

    @Test
    void requestBucketKey_twoSegmentsFromConcretePath() {
        assertEquals(
            "/basis/1",
            MatcherUtils.requestBucketKey(MatcherUtils.normalize("/basis/1/user"))
        );
    }

    @Test
    void requestBucketKey_exactlyTwoSegments() {
        assertEquals("/basis/user", MatcherUtils.requestBucketKey(MatcherUtils.normalize("/basis/user")));
    }

    @Test
    void requestBucketKey_singleSegment() {
        assertEquals("/api", MatcherUtils.requestBucketKey(MatcherUtils.normalize("/api")));
    }

    @Test
    void requestBucketKey_root() {
        assertEquals("/", MatcherUtils.requestBucketKey(MatcherUtils.normalize("/")));
    }
}
