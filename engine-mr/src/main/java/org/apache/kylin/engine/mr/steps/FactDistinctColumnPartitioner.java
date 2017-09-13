/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.kylin.engine.mr.steps;

import org.apache.hadoop.conf.Configurable;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Partitioner;
import org.apache.kylin.common.util.Bytes;
import org.apache.kylin.common.util.BytesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 */
public class FactDistinctColumnPartitioner extends Partitioner<SelfDefineSortableKey, Text> implements Configurable {
    private static final Logger logger = LoggerFactory.getLogger(FactDistinctColumnPartitioner.class);

    public static final String HLL_SHARD_BASE_PROPERTY_NAME = "mapreduce.partition.factdistinctcolumnpartitioner.hll.shard.base";

    public static void setHLLShard(Configuration conf, int hllShardBase) {
        conf.setInt(HLL_SHARD_BASE_PROPERTY_NAME, hllShardBase);
    }

    private Configuration conf;
    private int hllShardBase = 1;

    @Override
    public int getPartition(SelfDefineSortableKey skey, Text value, int numReduceTasks) {
        Text key = skey.getText();
        if (key.getBytes()[0] == FactDistinctColumnsMapper.MARK_FOR_HLL) {
            // the last $hllShard reducers are for merging hll
            Long cuboidId = Bytes.toLong(key.getBytes(), 1, Bytes.SIZEOF_LONG);
            int shard = cuboidId.hashCode() % hllShardBase;
            if (shard < 0) {
                shard += hllShardBase;
            }
            return numReduceTasks - shard - 1;
        } else if (key.getBytes()[0] == FactDistinctColumnsMapper.MARK_FOR_PARTITION_COL) {
            // the last but one reducer is for partition col
            return numReduceTasks - hllShardBase - 1;
        } else {
            return BytesUtil.readUnsigned(key.getBytes(), 0, 1);
        }
    }

    public void setConf(Configuration conf) {
        this.conf = conf;
        hllShardBase = conf.getInt(HLL_SHARD_BASE_PROPERTY_NAME, 1);
        logger.info("shard base for hll is " + hllShardBase);
    }

    public Configuration getConf() {
        return conf;
    }
}
