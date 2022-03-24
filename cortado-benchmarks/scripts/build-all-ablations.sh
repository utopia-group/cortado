#!/bin/bash

cd ../ ;

email=""
if [[ $# -ge 1 ]] ; then
    email=$1
fi;

# build_and_run_cortado  [frag_construction_method] [weight_method]
function build_and_run_cortado {
    if [ $# -le 1 ] ; then
        echo "Expected at least two arguments" ;
        exit 1 ;
    fi;
    frag_construction_method="$1"
    weight_method="$2"

    profile_options=""
    if [ "${frag_construction_method}" == "statement" ] ; then
        profile_options="${profile_options} -Dstatement.frags=true" ;
    elif [ "${frag_construction_method}" == "ccr" ] ; then
        profile_options="${profile_options} -Dccr.frags=true" ;
    elif [ ! "${frag_construction_method}" == "pt-based" ] ; then
        echo "Expected frag_construction_method to be 'statement', 'ccr', or 'pt-based', not '${frag_construction_method}'" ;
        exit 1;
    fi

    if [ "${weight_method}" == "uniform" ] ; then
        profile_options="${profile_options} -Duniform.weights=true" ;
    elif [ ! "${weight_method}" == "heuristic" ] ; then
        echo "Expected weight_method to be 'uniform' or 'heuristic', not '${weight_method}'" ;
        exit 1;
    fi

    make_cmd="mvn clean install ${profile_options}";

    data_dir="data" ;
    if [ ! -d ${data_dir} ] ; then
        mkdir "${data_dir}" ;
    fi

    impls_dir="cortado-example-monitor-impls"
    zip_file_base_name="${data_dir}/${weight_method}-${frag_construction_method}" ;
    zip_impls_name="${zip_file_base_name}-impls.zip" ;
    if [ ! -f "${zip_impls_name}" ]; then
        ${make_cmd} && zip -r "${zip_impls_name}" ${impls_dir}/target || exit 1;
    fi
    if [ ! "${email}" == "" ] ; then
        echo "Finished building ${zip_file_base_name}" | mail -s "Status Update" "${email}" ;
    fi
}

mvn clean install -f ../cortado-core -DskipTests || exit 1
# Only remove invariants for first run, since invariant computation is unrelated
# to fragment construction or weight selection
rm -f cortado-example-monitor-impls/target/*-inv.txt
build_and_run_cortado statement heuristic
build_and_run_cortado ccr heuristic
build_and_run_cortado pt-based heuristic
build_and_run_cortado pt-based uniform
