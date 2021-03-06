#!/usr/bin/env bash

#required environment variables
#ARTIFACTS_DIR

#optional envirinmnt dir
#SYSTEMTESTS_UPGRADED
#ENABLE_RBAC

CURDIR=`readlink -f \`dirname $0\``
source ${CURDIR}/test_func.sh
source "${CURDIR}/../../scripts/logger.sh"

TEST_PROFILE=${1}
TESTCASE=${2:-"io.enmasse.**"}
REG_API_SERVER=${3:-true}

info "Running tests with profile: ${TEST_PROFILE}, tests: ${TESTCASE}"

failure=0

#environment info before tests
LOG_DIR="${ARTIFACTS_DIR}/openshift-info/"
mkdir -p ${LOG_DIR}
get_kubernetes_info ${LOG_DIR} services default "-before"
get_kubernetes_info ${LOG_DIR} pods default "-before"

#start system resources logging
${CURDIR}/system-stats.sh > ${ARTIFACTS_DIR}/system-resources.log &
STATS_PID=$!
info "process for checking system resources is running with PID: ${STATS_PID}"

export_required_env
export OPENSHIFT_TOKEN=`oc whoami -t`

#start docker logging
DOCKER_LOG_DIR="${ARTIFACTS_DIR}/docker-logs"
${CURDIR}/docker-logs.sh ${DOCKER_LOG_DIR} > /dev/null 2> /dev/null &
LOGS_PID=$!
info "process for syncing docker logs is running with PID: ${LOGS_PID}"

#run tests
if [[ "${TEST_PROFILE}" = "systemtests-pr" ]]; then
    run_test ${TESTCASE} systemtests-shared-pr || failure=$(($failure + 1))
    run_test ${TESTCASE} systemtests-isolated-pr || failure=$(($failure + 1))
elif [[ "${TEST_PROFILE}" = "systemtests-marathon" ]] || [[ "${TEST_PROFILE}" = "systemtests-upgrade" ]]; then
    run_test ${TESTCASE} ${TEST_PROFILE}|| failure=$(($failure + 1))
elif [[ "${TEST_PROFILE}" = "systemtests-release" ]]; then
    run_test ${TESTCASE} systemtests-shared-release || failure=$(($failure + 1))
    run_test ${TESTCASE} systemtests-isolated-release || failure=$(($failure + 1))
else
    run_test ${TESTCASE} systemtests-shared || failure=$(($failure + 1))
    run_test ${TESTCASE} systemtests-isolated || failure=$(($failure + 1))
fi

#stop system resources logging
info "process for checking system resources with PID: ${STATS_PID} will be killed"
kill ${STATS_PID}

#stop docker logging
info "process for syncing docker logs with PID: ${LOGS_PID} will be killed"
kill ${LOGS_PID}
categorize_docker_logs "${DOCKER_LOG_DIR}" || true

if [ $failure -gt 0 ]
then
    err_and_exit "Systemtests failed"
elif [[ "${TEST_PROFILE}" != "systemtests-upgrade" ]]; then
    teardown_test ${OPENSHIFT_PROJECT}
fi
