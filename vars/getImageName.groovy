def getImageName(String tool) {
    switch (tool) {
        case 'gitleak':
            return 'zricethezav/gitleaks:latest'
        case 'owasp':
            return 'owasp/dependency-check-action:latest'
        case 'semgrep':
            return 'returntocorp/semgrep:latest'
        case 'checkov':
            return 'bridgecrew/checkov:latest'
        default:
            error "Unknown tool: ${tool}"
    }
}
