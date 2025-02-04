def call(String inputJson, String outputSarif) {
    sh """
        python3 jenkins-shared-libraries/resources/zap_json_to_sarif.py ${inputJson} ${outputSarif}
    """
}