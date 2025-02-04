def call(String inputJson, String outputSarif) {
    sh """
        python3 zap_json_to_sarif.py ${inputJson} ${outputSarif}
    """
}