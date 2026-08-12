package com.han.common.mybatis.observe;

import com.han.common.tenant.observe.MissingTenantContextRecorder;
import com.han.common.tenant.observe.MissingTenantContextSample;
import com.han.sample.caller.SampleBusinessCaller;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MissingTenantContextRecorderTest {

    @Test
    void aggregatesByOperationTableAndCallSite() {
        MissingTenantContextRecorder recorder = new MissingTenantContextRecorder(true, 60_000L);

        SampleBusinessCaller.recordDirectly(recorder, "SQL", "ai_agent");
        SampleBusinessCaller.recordDirectly(recorder, "SQL", "ai_agent");
        SampleBusinessCaller.recordDirectly(recorder, "INSERT", "ai_agent");

        List<MissingTenantContextSample> samples = recorder.snapshot();
        assertThat(samples).hasSize(2);
        assertThat(recorder.totalCount()).isEqualTo(3L);
        assertThat(samples).extracting(MissingTenantContextSample::operation).containsExactlyInAnyOrder("SQL", "INSERT");
        assertThat(samples).allSatisfy(sample ->
                assertThat(sample.callSite()).contains(SampleBusinessCaller.class.getName()));
    }

    @Test
    void nullValuesAreNormalizedInsteadOfBlowingUp() {
        MissingTenantContextRecorder recorder = new MissingTenantContextRecorder(true, 0L);

        recorder.record(null, null);

        assertThat(recorder.snapshot()).singleElement().satisfies(sample -> {
            assertThat(sample.operation()).isEqualTo("UNKNOWN");
            assertThat(sample.tableName()).isEqualTo("UNKNOWN");
        });
    }

    @Test
    void disabledRecorderCollectsNothing() {
        MissingTenantContextRecorder recorder = new MissingTenantContextRecorder(false, 0L);

        recorder.record("SQL", "ai_agent");

        assertThat(recorder.isEnabled()).isFalse();
        assertThat(recorder.snapshot()).isEmpty();
        assertThat(recorder.totalCount()).isZero();
    }

    @Test
    void resetClearsCounters() {
        MissingTenantContextRecorder recorder = new MissingTenantContextRecorder(true, 0L);
        recorder.record("SQL", "ai_agent");

        recorder.reset();

        assertThat(recorder.snapshot()).isEmpty();
    }
}
