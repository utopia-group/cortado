#!/usr/bin/env bash

# edu.utexas.cs.utopia.cortado.<path-to-benchmark>
declare -a BENCHMARKS=(
                       "githubbenchmarks.alluxio.worker.job.task.PausableThreadPoolExecutorBench"
                       "githubbenchmarks.com.ericsson.research.transport.ws.WSDataListenerBench"
                       "githubbenchmarks.ghidra.generic.concurrent.ProgressTrackerBench"
                       "githubbenchmarks.io.realm.internal.async.RealmThreadPoolExecutorBench"
                       "githubbenchmarks.java.util.concurrent.ArrayBlockingQueueBench"
                       "githubbenchmarks.org.apache.hadoop.metrics2.impl.SinkQueueBench"
                       "githubbenchmarks.org.apache.hive.spark.client.JobWrapperBench"
                       "githubbenchmarks.org.apache.kafka.trogdor.workload.RoundTripWorkerBench"
                       "githubbenchmarks.org.springframework.util.ConcurrencyThrottleSupportBench"
                       "githubbenchmarks.us.codecraft.webmagic.thread.CountableThreadPoolBench"
                      )
EXAMPLE_ARRAY_BLOCKING_QUEUE_QUEUE_CAPACITY="5";
JAVA_ARRAY_BLOCKING_QUEUE_QUEUE_CAPACITY="5"; # 0 uses default value = #nthreads
SINK_QUEUE_QUEUE_CAPACITY=5;
JOB_WRAPPER_BENCH_JOB_NUM=0;  # use default
ASYNC_DISPATCH_QUEUE_CAPACITY=5;
CONCURRENCY_THROTTLE_SUPPORT_THREAD_LIMIT=5;
COUNTABLE_THREAD_POOL_BENCH_THREAD_NUM=5;

# JMH arguments for each benchmark, delimited by |. Default is
#DEFAULT_BENCH_JMH_BENCH_PARAMS="-t 8|-t 16|-t 32|-t 48|-t 64|-t 80";
#DEFAULT_BENCH_JMH_BENCH_PARAMS="-t 160|-t 192|-t 224|-t 256";
DEFAULT_BENCH_JMH_BENCH_PARAMS="-t 2|-t 4|-t 8|-t 16|-t 32|-t 48|-t 64|-t 80|-t 96|-t 112|-t 128|-t 160|-t 192|-t 224|-t 256";
# Comment this out to avoid only running the implicit benchmarks
#DEFAULT_BENCH_JMH_BENCH_PARAMS=${DEFAULT_BENCH_JMH_BENCH_PARAMS//|/ -p whichImplementation=Implicit|}" -p whichImplementation=Implicit"
declare -A BENCH_JMH_BENCH_PARAMS=(
                           ["examplemonitors.blockingqueue.ArrayBlockingQueueBench"]="\
${DEFAULT_BENCH_JMH_BENCH_PARAMS//|/ -p queueCapacityString=$EXAMPLE_ARRAY_BLOCKING_QUEUE_QUEUE_CAPACITY|}\
 -p queueCapacityString=$EXAMPLE_ARRAY_BLOCKING_QUEUE_QUEUE_CAPACITY"
                           ["githubbenchmarks.com.alibaba.nacos.common.utils.ObservableBench"]="\
-t 3|-t 6|-t 9|-t 18|-t 33|-t 48|-t 66|-t 81|-t 96|-t 114|-t 129"
                           ["githubbenchmarks.java.util.concurrent.ArrayBlockingQueueBench"]="\
${DEFAULT_BENCH_JMH_BENCH_PARAMS//|/ -p queueCapacityString=$JAVA_ARRAY_BLOCKING_QUEUE_QUEUE_CAPACITY|}\
 -p queueCapacityString=$JAVA_ARRAY_BLOCKING_QUEUE_QUEUE_CAPACITY"
                           ["githubbenchmarks.org.apache.flink.streaming.connectors.kafka.internals.ClosableBlockingQueueBench"]="\
-t 5|-t 10|-t 20|-t 35|-t 50|-t 65|-t 80|-t 95|-t 110|-t 130"  # must be multiple of 5
                           ["githubbenchmarks.org.apache.hadoop.metrics2.impl.SinkQueueBench"]="\
${DEFAULT_BENCH_JMH_BENCH_PARAMS//|/ -p queueCapacityString=$SINK_QUEUE_QUEUE_CAPACITY|}\
 -p queueCapacityString=$SINK_QUEUE_QUEUE_CAPACITY"
                           ["githubbenchmarks.com.linkedin.databus.core.DataBusThreadBaseBench"]="-t 2"  # must use exactly 2 threads
                           ["githubbenchmarks.org.apache.hive.spark.client.JobWrapperBench"]="\
${DEFAULT_BENCH_JMH_BENCH_PARAMS//|/ -p numJobsString=$JOB_WRAPPER_BENCH_JOB_NUM|}\
 -p numJobsString=$JOB_WRAPPER_BENCH_JOB_NUM"
                           ["githubbenchmarks.org.gradle.internal.dispatch.AsyncDispatchBench"]="\
${DEFAULT_BENCH_JMH_BENCH_PARAMS//|/ -p queueCapacityString=$ASYNC_DISPATCH_QUEUE_CAPACITY|}\
 -p queueCapacityString=$ASYNC_DISPATCH_QUEUE_CAPACITY"
                           ["githubbenchmarks.org.springframework.util.ConcurrencyThrottleSupportBench"]="\
${DEFAULT_BENCH_JMH_BENCH_PARAMS//|/ -p threadLimitString=$CONCURRENCY_THROTTLE_SUPPORT_THREAD_LIMIT|}\
 -p threadLimitString=$CONCURRENCY_THROTTLE_SUPPORT_THREAD_LIMIT"
                           ["githubbenchmarks.us.codecraft.webmagic.thread.CountableThreadPoolBench"]="\
${DEFAULT_BENCH_JMH_BENCH_PARAMS//|/ -p threadNumString=$COUNTABLE_THREAD_POOL_BENCH_THREAD_NUM|}\
 -p threadNumString=$COUNTABLE_THREAD_POOL_BENCH_THREAD_NUM"
                         )

# Parameters to be passed to JVM for each benchmark. Also delimited by |
DEFAULT_JVM_PARAMS="";
declare -A BENCH_JVM_PARAMS=(
                        )
# path to example monitors jar
EXAMPLE_MONITORS_JAR=$(realpath ../cortado-example-monitor-impls/target/cortado-example-monitor-impls-0.0.1-SNAPSHOT.jar)
if [ ! -f "${EXAMPLE_MONITORS_JAR}" ]; then
    echo "Example monitors file does not exist: ${EXAMPLE_MONITORS_JAR}"
    exit 1
fi
echo "Example monitors jar: ${EXAMPLE_MONITORS_JAR}"
BENCHMARKS_JAR=$(realpath target/benchmarks.jar)
if [ ! -f "${BENCHMARKS_JAR}" ]; then
    echo "Benchmarks jar file does not exist: ${BENCHMARKS_JAR}"
    exit 1
fi
[ -d results ] || mkdir results  # make directory results/ if it does not already exist
RESULTS_DIR=$(realpath results)
# benchmark configuring
DEFAULT_NUM_BENCHMARK_FORKS=3
DEFAULT_NUM_WARMUP_ITERATIONS=5
DEFAULT_NUM_MEASUREMENT_ITERATIONS=5
# Set this to override benchmark default batch sizes
DEFAULT_JMH_BATCH_SIZE=10000
# false if you want to keep per-function benchmarks, (e.g. put() and take() individually)
# true to discard them.
REMOVE_SECONDARY_RESULTS=false;

# custom number of forks|warmup|measurement|batchsize
DEFAULT_JMH_ITERATION_PARAMS="${DEFAULT_NUM_BENCHMARK_FORKS}|${DEFAULT_NUM_WARMUP_ITERATIONS}|\
${DEFAULT_NUM_MEASUREMENT_ITERATIONS}|${DEFAULT_JMH_BATCH_SIZE}"
declare -A JMH_ITERATION_PARAMS=(
    ["githubbenchmarks.alluxio.worker.job.task.PausableThreadPoolExecutorBench"]="${DEFAULT_NUM_BENCHMARK_FORKS}|${DEFAULT_NUM_WARMUP_ITERATIONS}|\
${DEFAULT_NUM_MEASUREMENT_ITERATIONS}|40000"
    ["githubbenchmarks.com.ericsson.research.transport.ws.WSDataListenerBench"]="${DEFAULT_NUM_BENCHMARK_FORKS}|${DEFAULT_NUM_WARMUP_ITERATIONS}|\
${DEFAULT_NUM_MEASUREMENT_ITERATIONS}|10000"
    ["githubbenchmarks.ghidra.generic.concurrent.ProgressTrackerBench"]="${DEFAULT_NUM_BENCHMARK_FORKS}|${DEFAULT_NUM_WARMUP_ITERATIONS}|\
${DEFAULT_NUM_MEASUREMENT_ITERATIONS}|50000"
    ["githubbenchmarks.io.realm.internal.async.RealmThreadPoolExecutorBench"]="${DEFAULT_NUM_BENCHMARK_FORKS}|${DEFAULT_NUM_WARMUP_ITERATIONS}|\
${DEFAULT_NUM_MEASUREMENT_ITERATIONS}|40000"
    ["githubbenchmarks.java.util.concurrent.ArrayBlockingQueueBench"]="3|10|10|1000"
    ["githubbenchmarks.org.apache.hadoop.metrics2.impl.SinkQueueBench"]="${DEFAULT_NUM_BENCHMARK_FORKS}|${DEFAULT_NUM_WARMUP_ITERATIONS}|\
${DEFAULT_NUM_MEASUREMENT_ITERATIONS}|10000"
    ["githubbenchmarks.org.apache.hive.spark.client.JobWrapperBench"]="3|20|25|500"
    ["githubbenchmarks.org.apache.kafka.trogdor.workload.RoundTripWorkerBench"]="3|10|10|1000"
    ["githubbenchmarks.org.springframework.util.ConcurrencyThrottleSupportBench"]="${DEFAULT_NUM_BENCHMARK_FORKS}|${DEFAULT_NUM_WARMUP_ITERATIONS}|\
${DEFAULT_NUM_MEASUREMENT_ITERATIONS}|7000"
)

# sets OUT_CSV to out-csv name
#
# Usage:
# set_csv_name benchmark_name [benchmark param]
function set_csv_name
{
    if [ $# == 1 ]
    then
        OUT_CSV=${RESULTS_DIR}/${1}".csv"
    elif [ $# == 2 ]
    then
        OUT_CSV=${RESULTS_DIR}/${1}-${2}".csv"
    else
        echo "Invalid number of arguments to set_csv_name"
        exit 4
    fi
}

# Usage:
# run_bench benchmark_name [benchmark param]
function run_bench
{
    # Get the |-delimited
    # benchmark configurations or the default DEFAULT_BENCH_JMH_BENCH_PARAMS
    IFS='|' read -r -a configs <<< "${BENCH_JMH_BENCH_PARAMS[${1}]:-${DEFAULT_BENCH_JMH_BENCH_PARAMS}}"

    # Read the benchmark iteration parameters
    IFS='|' read -r -a iteration_params <<< "${JMH_ITERATION_PARAMS[${1}]:-${DEFAULT_JMH_ITERATION_PARAMS}}"
    if [ "${#iteration_params[@]}" -ne 4 ]
    then
        printf "Expected 4 iteration parameters, received %s" % ${#iteration_params[@]}
        exit 1
    fi;
    NUM_BENCHMARK_FORKS=${iteration_params[0]};
    NUM_WARMUP_ITERATIONS=${iteration_params[1]};
    NUM_MEASUREMENT_ITERATIONS=${iteration_params[2]};
    JMH_BATCH_SIZE=${iteration_params[3]};

    if [ $# == 1 ] 
    then
        set_csv_name "${1}"
    else
        set_csv_name "${1}" "${2}"
    fi

    if [ -f "${OUT_CSV}" ] ; then
        echo "${OUT_CSV} already exists. Skipping"
        return
    fi

    TEMP_CSV=${RESULTS_DIR}/tmp.csv

    # make sure we have at least one configuration
    if [ ${#configs[@]} -eq 0 ] 
    then
        echo "No configuration for benchmark ${1}"
        exit 3
    fi;

    echo "Running benchmark ${1} with configs:"
    for config in "${configs[@]}"
    do
        echo "$config"
    done

    batch_size_args="";
    if [[ -n ${JMH_BATCH_SIZE} ]] ;
    then
        batch_size_args="-bs ${JMH_BATCH_SIZE} -wbs ${JMH_BATCH_SIZE}";
        echo "Setting batch size to ${JMH_BATCH_SIZE}"
    fi;

    # Run benchmark with each configuration
    for config in "${configs[@]}"
    do
        # shellcheck disable=SC2086
        # Configuration:
        # -gc true             Force gc between iterations
        # -foe true            fail on error
        # -rff ${TEMP_CSV}     output to ${TEMP_CSV}
        # -tu ms               measure with time-unit of milliseconds
        java ${2} -ea \
            -Xmx3g -Xms3g -XX:-TieredCompilation \
            -cp ${EXAMPLE_MONITORS_JAR} \
            -jar ${BENCHMARKS_JAR} \
                -gc true \
                -foe true \
                -wi ${NUM_WARMUP_ITERATIONS} \
                -i ${NUM_MEASUREMENT_ITERATIONS} \
                -f ${NUM_BENCHMARK_FORKS} \
                -rff "${TEMP_CSV}" \
                -tu ms \
                ${batch_size_args} \
                ${config} \
                "edu.utexas.cs.utopia.cortado.${1}"

        # remove secondary results
        if [ "${REMOVE_SECONDARY_RESULTS}" = true ] ;
        then
            awk '!/^.*:.*$/' "${TEMP_CSV}" > "${TEMP_CSV}".tmp
            mv "${TEMP_CSV}".tmp "${TEMP_CSV}"
        fi

        # if OUT_CSV doesn't exist yet, make it (with the header)
        if [ ! -f "${OUT_CSV}" ] ;
        then
            mv "${TEMP_CSV}" "${OUT_CSV}"
        # Otherwise, append to OUT_CSV without the header
        else
            tail -n +2 "${TEMP_CSV}" >> "${OUT_CSV}"
        fi;
    done
}

# make results directory if it doesn't already exist
mkdir -p "${RESULTS_DIR}"

# Make sure all the benchmarks are java files that exist
# and end in "Bench"
echo "Verifying requested benchmarks exist."
for bench in "${BENCHMARKS[@]}"
do
    # require bench ends in "Bench"
    if [[ ! ${bench} =~ .*Bench$ ]] ;
    then
        echo "Benchmark name ${bench} does not end with suffix \"Bench\""
        exit 1
    fi;
    # class file with . replaced by /
    class_file=$(echo "edu.utexas.cs.utopia.cortado.${bench}" | tr \. /)
    class_file="${class_file}.class"
    # see if class file in jar
    tf_check=$(jar tf "${BENCHMARKS_JAR}" "${class_file}")
    # if class file not in jar, raise error
    if [ "${tf_check}" != "${class_file}" ] ;
    then
        echo "${BENCHMARKS_JAR} missing class file"
        echo "${class_file}"
        exit 1
    else
        echo "${bench} found"
    fi;
done

printf "All benchmarks found. Now starting JMH run\n\n"

# Now run all the benchmarks
for bench in "${BENCHMARKS[@]}"
do
    # Read |-delimited parameters to into params with default DEFAULT_JVM_PARAMS
    IFS='|' read -r -a params <<< "${BENCH_JVM_PARAMS[${bench}]:-${DEFAULT_JVM_PARAMS}}"

    if [ ${#params[@]} -eq 0 ] ;
    then
        # no parameters case
        run_bench "${bench}"
    else
        # Run with each parameter
        for param in "${params[@]}";
        do
            run_bench "${bench}" "${param}"
        done
    fi
done
