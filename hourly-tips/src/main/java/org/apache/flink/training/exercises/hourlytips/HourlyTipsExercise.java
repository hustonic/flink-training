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

package org.apache.flink.training.exercises.hourlytips;

import org.apache.flink.api.common.JobExecutionResult;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.PrintSinkFunction;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.streaming.api.functions.source.SourceFunction;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.training.exercises.common.datatypes.TaxiFare;
import org.apache.flink.training.exercises.common.sources.TaxiFareGenerator;
import org.apache.flink.util.Collector;

/**
 * The Hourly Tips exercise from the Flink training.
 *
 * <p>The task of the exercise is to first calculate the total tips collected by each driver, hour
 * by hour, and then from that stream, find the highest tip total in each hour.
 */
public class HourlyTipsExercise {

    private final SourceFunction<TaxiFare> source;
    private final SinkFunction<Tuple3<Long, Long, Float>> sink;

    /**
     * Creates a job using the source and sink provided.
     */
    public HourlyTipsExercise(
            SourceFunction<TaxiFare> source, SinkFunction<Tuple3<Long, Long, Float>> sink) {

        this.source = source;
        this.sink = sink;
    }

    /**
     * Main method.
     *
     * @throws Exception which occurs during job execution.
     */
    public static void main(String[] args) throws Exception {

        HourlyTipsExercise job =
                new HourlyTipsExercise(new TaxiFareGenerator(), new PrintSinkFunction<>());

        job.execute();
    }

    /**
     * Create and execute the hourly tips pipeline.
     *
     * @return {JobExecutionResult}
     * @throws Exception which occurs during job execution.
     */
    public JobExecutionResult execute() throws Exception {

        // set up streaming execution environment
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // start the data generator
        DataStream<TaxiFare> fares = env
                .addSource(source)
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<TaxiFare>forMonotonousTimestamps()
                                .withTimestampAssigner((fare, t) -> fare.getEventTimeMillis()));

        // replace this with your solution
        DataStream<Tuple3<Long, Long, Float>> totalTips = fares
                .keyBy(fare -> fare.driverId)
                .window(TumblingEventTimeWindows.of(Time.hours(1)))
                .aggregate(new SumAggregationFunction(), new SumProcessWindowFunction());

        DataStream<Tuple3<Long, Long, Float>> maxTotalTips = totalTips
                .windowAll(TumblingEventTimeWindows.of(Time.hours(1)))
                //.reduce((ReduceFunction<Tuple3<Long, Long, Float>>) (value1, value2) -> value1.f2 > value2.f2 ? value1 : value2);
                .maxBy(2);

        maxTotalTips.addSink(sink);

        // the results should be sent to the sink that was passed in
        // (otherwise the tests won't work)
        // you can end the pipeline with something like this:

        // DataStream<Tuple3<Long, Long, Float>> hourlyMax = ...
        // hourlyMax.addSink(sink);

        // execute the pipeline and return the result
        return env.execute("Hourly Tips");
    }

    public static class SumAggregationFunction implements AggregateFunction<TaxiFare, Float, Float> {

        @Override
        public Float createAccumulator() {
            return 0F;
        }

        @Override
        public Float add(TaxiFare value, Float accumulator) {
            return accumulator + value.tip;
        }

        @Override
        public Float getResult(Float accumulator) {
            return accumulator;
        }

        @Override
        public Float merge(Float a, Float b) {
            return a + b;
        }
    }

    public static class SumProcessWindowFunction extends ProcessWindowFunction<Float, Tuple3<Long, Long, Float>, Long, TimeWindow> {
        @Override
        public void process(Long key, Context context, Iterable<Float> input, Collector<Tuple3<Long, Long, Float>> out) throws Exception {
            long endTime = context.window().getEnd();
            Float sum = input.iterator().next();
            out.collect(Tuple3.of(endTime, key, sum));
        }
    }
}
