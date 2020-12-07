package com.amazon.dataprepper.model.buffer;

import com.amazon.dataprepper.model.configuration.PluginSetting;
import com.amazon.dataprepper.metrics.MetricNames;
import com.amazon.dataprepper.metrics.MetricsTestUtil;
import com.amazon.dataprepper.model.record.Record;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Statistic;
import org.junit.Assert;
import org.junit.Test;

public class AbstractBufferTest {

    @Test
    public void testReadAndWriteMetrics() throws TimeoutException {
        final String bufferName = "testBuffer";
        final String pipelineName = "pipelineName";
        MetricsTestUtil.initMetrics();

        PluginSetting pluginSetting = new PluginSetting(bufferName, Collections.emptyMap());
        pluginSetting.setPipelineName(pipelineName);
        final AbstractBuffer<Record<String>> abstractBuffer = new AbstractBufferImpl(pluginSetting);
        for(int i=0; i<5; i++) {
            abstractBuffer.write(new Record<>(UUID.randomUUID().toString()), 1000);
        }
        abstractBuffer.read(1000);

        final List<Measurement> recordsWrittenMeasurements = MetricsTestUtil.getMeasurementList(
                new StringJoiner(MetricNames.DELIMITER).add(pipelineName).add(bufferName).add(MetricNames.RECORDS_WRITTEN).toString());
        final List<Measurement> recordsReadMeasurements = MetricsTestUtil.getMeasurementList(
                new StringJoiner(MetricNames.DELIMITER).add(pipelineName).add(bufferName).add(MetricNames.RECORDS_READ).toString());
        final List<Measurement> writeTimeMeasurements = MetricsTestUtil.getMeasurementList(
                new StringJoiner(MetricNames.DELIMITER).add(pipelineName).add(bufferName).add(MetricNames.WRITE_TIME_ELAPSED).toString());
        final List<Measurement> readTimeMeasurements = MetricsTestUtil.getMeasurementList(
                new StringJoiner(MetricNames.DELIMITER).add(pipelineName).add(bufferName).add(MetricNames.READ_TIME_ELAPSED).toString());
        Assert.assertEquals(1, recordsWrittenMeasurements.size());
        Assert.assertEquals(5.0, recordsWrittenMeasurements.get(0).getValue(), 0);
        Assert.assertEquals(1, recordsReadMeasurements.size());
        Assert.assertEquals(5.0, recordsReadMeasurements.get(0).getValue(), 0);
        Assert.assertEquals(5.0, MetricsTestUtil.getMeasurementFromList(writeTimeMeasurements, Statistic.COUNT).getValue(), 0);
        Assert.assertTrue(
                MetricsTestUtil.isBetween(
                        MetricsTestUtil.getMeasurementFromList(writeTimeMeasurements, Statistic.TOTAL_TIME).getValue(),
                        0.5,
                        0.6));
        Assert.assertEquals(1.0, MetricsTestUtil.getMeasurementFromList(readTimeMeasurements, Statistic.COUNT).getValue(), 0);
        Assert.assertTrue(MetricsTestUtil.isBetween(
                MetricsTestUtil.getMeasurementFromList(readTimeMeasurements, Statistic.TOTAL_TIME).getValue(),
                0.1,
                0.2));
    }

    @Test
    public void testTimeoutMetric() throws TimeoutException {
        final String bufferName = "testBuffer";
        final String pipelineName = "pipelineName";
        MetricsTestUtil.initMetrics();
        PluginSetting pluginSetting = new PluginSetting(bufferName, Collections.emptyMap());
        pluginSetting.setPipelineName(pipelineName);
        final AbstractBuffer<Record<String>> abstractBuffer = new AbstractBufferTimeoutImpl(pluginSetting);
        Assert.assertThrows(TimeoutException.class, () -> abstractBuffer.write(new Record<>(UUID.randomUUID().toString()), 1000));

        final List<Measurement> timeoutMeasurements = MetricsTestUtil.getMeasurementList(
                new StringJoiner(MetricNames.DELIMITER).add(pipelineName).add(bufferName).add(MetricNames.WRITE_TIMEOUTS).toString());
        Assert.assertEquals(1, timeoutMeasurements.size());
        Assert.assertEquals(1.0, timeoutMeasurements.get(0).getValue(), 0);
    }

    @Test
    public void testRuntimeException() {
        final String bufferName = "testBuffer";
        final String pipelineName = "pipelineName";
        MetricsTestUtil.initMetrics();
        PluginSetting pluginSetting = new PluginSetting(bufferName, Collections.emptyMap());
        pluginSetting.setPipelineName(pipelineName);
        final AbstractBuffer<Record<String>> abstractBuffer = new AbstractBufferNpeImpl(pluginSetting);
        Assert.assertThrows(NullPointerException.class, () -> abstractBuffer.write(new Record<>(UUID.randomUUID().toString()), 1000));
    }

    public static class AbstractBufferImpl extends AbstractBuffer<Record<String>> {
        private final Queue<Record<String>> queue;
        public AbstractBufferImpl(PluginSetting pluginSetting) {
            super(pluginSetting);
            queue = new LinkedList<>();
        }

        @Override
        public void doWrite(Record<String> record, int timeoutInMillis) throws TimeoutException {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {

            }
            queue.add(record);
        }

        @Override
        public Collection<Record<String>> doRead(int timeoutInMillis) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {

            }
            final Collection<Record<String>> records = new HashSet<>();
            for(int i=0; i<5; i++) {
                if(!queue.isEmpty()) {
                    records.add(queue.remove());
                }
            }
            return records;
        }
    }

    public static class AbstractBufferTimeoutImpl extends AbstractBufferImpl {
        public AbstractBufferTimeoutImpl(PluginSetting pluginSetting) {
            super(pluginSetting);
        }

        @Override
        public void doWrite(Record<String> record, int timeoutInMillis) throws TimeoutException {
            throw new TimeoutException();
        }
    }

    public static class AbstractBufferNpeImpl extends AbstractBufferImpl {
        public AbstractBufferNpeImpl(PluginSetting pluginSetting) {
            super(pluginSetting);
        }

        @Override
        public void doWrite(Record<String> record, int timeoutInMillis) throws TimeoutException {
            throw new NullPointerException();
        }
    }
}