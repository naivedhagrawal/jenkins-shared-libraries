#!/usr/bin/env python3
import json
import uuid
import re
import sys
import os

def remove_p_tags(obj):
    if isinstance(obj, str):
        return re.sub(r'<p>.*?</p>', '', obj)
    elif isinstance(obj, list):
        return [remove_p_tags(item) for item in obj]
    elif isinstance(obj, dict):
        return {key: remove_p_tags(value) for key, value in obj.items()}
    else:
        return obj

def extract_alerts(data):
    """Recursively search for alerts in any JSON structure"""
    if isinstance(data, dict):
        if "alerts" in data:
            return data["alerts"]
        for value in data.values():
            result = extract_alerts(value)
            if result:
                return result
    elif isinstance(data, list):
        for item in data:
            result = extract_alerts(item)
            if result:
                return result
    return []

def sanitize_uri(uri):
    if not uri or uri.isdigit() or re.match(r'^/\S+', uri):
        return "unknown-file"
    if uri.startswith("http"):
        return uri  # Keep external URLs unchanged
    return os.path.normpath(uri)  # Normalize internal paths

def main(input_file="zap-out.json", output_file="zap_report.sarif"):
    workspace = os.getenv("WORKSPACE", "./")  # Get Jenkins workspace path
    
    try:
        with open(input_file, "r", encoding="utf-8") as file:
            input_data = json.load(file)
    except FileNotFoundError:
        print(f"Error: Could not find {input_file}")
        exit(1)
    except json.JSONDecodeError:
        print(f"Error: {input_file} is not a valid JSON file")
        exit(1)
    
    sarif_report = {
        "version": "2.1.0",
        "$schema": "https://json.schemastore.org/sarif-2.1.0.json",
        "runs": [{
            "tool": {
                "driver": {
                    "name": "Security Scanner",
                    "informationUri": "https://www.zaproxy.org/",
                    "version": "2.12.0",
                    "rules": []
                }
            },
            "results": []
        }]
    }
    
    alerts = extract_alerts(input_data)
    severity_map = {"High": "error", "Medium": "warning", "Low": "note", "Informational": "none"}
    
    for alert in alerts:
        rule_id = str(uuid.uuid4())[:8]
        risk = alert.get("riskdesc", "").split(" ")[0]
        severity = severity_map.get(risk, "none")

        sarif_report["runs"][0]["tool"]["driver"]["rules"].append({
            "id": rule_id,
            "name": alert.get("name", "Unknown Issue"),
            "shortDescription": {"text": alert.get("name", "No description available.")},
            "fullDescription": {"text": alert.get("desc", "No full description available.")},
            "helpUri": alert.get("reference", ""),
            "help": {"text": alert.get("solution", "No solution available.")},
            "properties": {"problem.severity": severity}
        })

        for instance in alert.get("instances", []):
            artifact_path = sanitize_uri(instance.get("uri", "Unknown"))
            print(f"Debug: Processed URI - {artifact_path}")  # Debugging log
            
            sarif_report["runs"][0]["results"].append({
                "ruleId": rule_id,
                "level": severity,
                "message": {"text": alert.get("desc", "No description available.")},
                "locations": [{
                    "physicalLocation": {
                        "artifactLocation": {"uri": artifact_path},
                        "region": {"startLine": 1}
                    }
                }]
            })
    
    sarif_report = remove_p_tags(sarif_report)
    try:
        with open(output_file, "w", encoding="utf-8") as sarif_file:
            json.dump(sarif_report, sarif_file, indent=4)
        print(f"SARIF report generated and cleaned: {output_file}")
    except IOError:
        print(f"Error: Could not write to {output_file}")
        exit(1)

if __name__ == "__main__":
    input_file = sys.argv[1] if len(sys.argv) > 1 else "zap-out.json"
    output_file = sys.argv[2] if len(sys.argv) > 2 else "zap_report.sarif"
    main(input_file, output_file)
