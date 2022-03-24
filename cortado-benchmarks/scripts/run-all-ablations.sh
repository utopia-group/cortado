#!/bin/bash

impls_dir="../cortado-benchmark-implementations"
bench_dir="../cortado-benchmark-harnesses"
for i in ../data/*-impls.zip ; do
    # Clean out old target
    tgt_dir="${impls_dir}/target"
    rm -r "${tgt_dir}"

    # Copy pre-built target from zip file
    echo "Copying pre-built target into ${tgt_dir}"
    unzip "${i}" -d ".."

    # Install benchmarks with the pre-built target
    echo "Installing pre-built ${tgt_dir} to local maven repository"
    mvn install -Dmaven.main.skip=true -Dexec.skip=true \
        -f ${impls_dir} -Dsolver.exec="${Z3_SOLVER_EXEC}"
    echo "Building benchmarks"
    mvn clean install -f ${bench_dir}

    # Now run benchmarks
    rm -r ${bench_dir}/results
    (cd ${bench_dir} && ./run-all-benchmarks.sh)
    # save results to data
    data_file=${i%%-impls.zip}-data.zip ;
    if [ -f "${data_file}" ] ; then
        # Make a temporary directory
        tmp_dir=".tmp";
        if [ -d "${tmp_dir}" ] ; then
          echo "Directory ${tmp_dir} already exists"
          exit 1
        fi
        mkdir "${tmp_dir}" ;
        # Merge any old results into the new results
        unzip "${data_file}" -d "${tmp_dir}" ;
        results_dir="${bench_dir}/results";
        for new_results_file in "${results_dir}"/* ; do
            echo "${new_results_file} in ${results_dir}"
            file_base_name=$(basename "${new_results_file}");
            old_results_file="${tmp_dir}/cortado-example-monitor-benchmarks/results/${file_base_name}" ;
            if [ -f "${old_results_file}" ] ; then
                python3 merge_csvs.py "${new_results_file}" "${old_results_file}" -o "${new_results_file}" ;
            fi
        done
        rm -r "${tmp_dir}" ;
    fi
    # Save the data into the zip file
    zip "${data_file}" -r ${bench_dir}/results
done
