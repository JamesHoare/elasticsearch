/*
 * Licensed to Elastic Search and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Elastic Search licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.indices.recovery;

import org.elasticsearch.cluster.metadata.MetaData;
import org.elasticsearch.common.component.AbstractComponent;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.ByteSizeUnit;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.util.concurrent.DynamicExecutors;
import org.elasticsearch.common.util.concurrent.EsExecutors;
import org.elasticsearch.node.settings.NodeSettingsService;

import java.util.concurrent.ThreadPoolExecutor;

/**
 */
public class RecoverySettings extends AbstractComponent {

    static {
        MetaData.addDynamicSettings("indices.recovery.file_chunk_size");
        MetaData.addDynamicSettings("indices.recovery.translog_ops");
        MetaData.addDynamicSettings("indices.recovery.translog_size");
        MetaData.addDynamicSettings("indices.recovery.compress");
        MetaData.addDynamicSettings("`");
    }

    private volatile ByteSizeValue fileChunkSize;

    private volatile boolean compress;
    private volatile int translogOps;
    private volatile ByteSizeValue translogSize;

    private volatile int concurrentStreams;
    private final ThreadPoolExecutor concurrentStreamPool;

    @Inject public RecoverySettings(Settings settings, NodeSettingsService nodeSettingsService) {
        super(settings);

        this.fileChunkSize = componentSettings.getAsBytesSize("file_chunk_size", settings.getAsBytesSize("index.shard.recovery.file_chunk_size", new ByteSizeValue(100, ByteSizeUnit.KB)));
        this.translogOps = componentSettings.getAsInt("translog_ops", settings.getAsInt("index.shard.recovery.translog_ops", 1000));
        this.translogSize = componentSettings.getAsBytesSize("translog_size", settings.getAsBytesSize("index.shard.recovery.translog_size", new ByteSizeValue(100, ByteSizeUnit.KB)));
        this.compress = componentSettings.getAsBoolean("compress", true);

        this.concurrentStreams = componentSettings.getAsInt("concurrent_streams", settings.getAsInt("index.shard.recovery.concurrent_streams", 5));
        this.concurrentStreamPool = (ThreadPoolExecutor) DynamicExecutors.newScalingThreadPool(1, concurrentStreams, TimeValue.timeValueSeconds(5).millis(), EsExecutors.daemonThreadFactory(settings, "[recovery_stream]"));

        logger.debug("using concurrent_streams [{}], file_chunk_size [{}], translog_size [{}], translog_ops [{}], and compress [{}]",
                concurrentStreams, fileChunkSize, translogSize, translogOps, compress);

        nodeSettingsService.addListener(new ApplySettings());
    }

    public void close() {
        concurrentStreamPool.shutdown();
    }

    public ByteSizeValue fileChunkSize() {
        return fileChunkSize;
    }

    public boolean compress() {
        return compress;
    }

    public int translogOps() {
        return translogOps;
    }

    public ByteSizeValue translogSize() {
        return translogSize;
    }

    public int concurrentStreams() {
        return concurrentStreams;
    }

    public ThreadPoolExecutor concurrentStreamPool() {
        return concurrentStreamPool;
    }

    class ApplySettings implements NodeSettingsService.Listener {
        @Override public void onRefreshSettings(Settings settings) {
            ByteSizeValue fileChunkSize = settings.getAsBytesSize("indices.recovery.file_chunk_size", RecoverySettings.this.fileChunkSize);
            if (!fileChunkSize.equals(RecoverySettings.this.fileChunkSize)) {
                logger.info("updating [indices.recovery.file_chunk_size] from [{}] to [{}]", RecoverySettings.this.fileChunkSize, fileChunkSize);
                RecoverySettings.this.fileChunkSize = fileChunkSize;
            }

            int translogOps = settings.getAsInt("indices.recovery.translog_ops", RecoverySettings.this.translogOps);
            if (translogOps != RecoverySettings.this.translogOps) {
                logger.info("updating [indices.recovery.translog_ops] from [{}] to [{}]", RecoverySettings.this.translogOps, translogOps);
                RecoverySettings.this.translogOps = translogOps;
            }

            ByteSizeValue translogSize = settings.getAsBytesSize("indices.recovery.translog_size", RecoverySettings.this.translogSize);
            if (!translogSize.equals(RecoverySettings.this.translogSize)) {
                logger.info("updating [indices.recovery.translog_size] from [{}] to [{}]", RecoverySettings.this.translogSize, translogSize);
                RecoverySettings.this.translogSize = translogSize;
            }

            boolean compress = settings.getAsBoolean("indices.recovery.compress", RecoverySettings.this.compress);
            if (compress != RecoverySettings.this.compress) {
                logger.info("updating [indices.recovery.compress] from [{}] to [{}]", RecoverySettings.this.compress, compress);
                RecoverySettings.this.compress = compress;
            }

            int concurrentStreams = settings.getAsInt("indices.recovery.concurrent_streams", RecoverySettings.this.concurrentStreams);
            if (concurrentStreams != RecoverySettings.this.concurrentStreams) {
                logger.info("updating [indices.recovery.concurrent_streams] from [{}] to [{}]", RecoverySettings.this.concurrentStreams, concurrentStreams);
                RecoverySettings.this.concurrentStreams = concurrentStreams;
                RecoverySettings.this.concurrentStreamPool.setMaximumPoolSize(concurrentStreams);
            }
        }
    }
}