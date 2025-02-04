def call(String inputJson, String outputSarif) {
    sh """
        sh "cp ${JENKINS_HOME}/workspace/${JOB_NAME}@libs/k8s-shared-lib/resources/zap_json_to_sarif.py ."
        python3 zap_json_to_sarif.py ${inputJson} ${outputSarif}
    """
}