def call(String inputJson, String outputSarif) {
    sh """
        def jsonToSarif = libraryResource('zap_json_to_sarif.py')
        writeFile file: 'zap_json_to_sarif.py', text: jsonToSarif
        python3 zap_json_to_sarif.py ${inputJson} ${outputSarif}
    """
}