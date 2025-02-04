#!/usr/bin/env python3
import json
import uuid
import re

ZAP_JSON_FILE = "zap-out.json"
SARIF_FILE = "zap_report.sarif"

                            # Function to remove <p> tags from a string
                            def remove_p_tags(obj):
                                if isinstance(obj, str):
                                    return re.sub(r'<p>.*?</p>', '', obj)  # Removing <p> tags from strings
                                elif isinstance(obj, list):
                                    return [remove_p_tags(item) for item in obj]
                                elif isinstance(obj, dict):
                                    return {key: remove_p_tags(value) for key, value in obj.items()}
                                else:
                                    return obj

                            # Read ZAP JSON file
                            with open(ZAP_JSON_FILE, "r") as file:
                                zap_data = json.load(file)

                            # Convert to SARIF format
                            sarif_report = {
                                "version": "2.1.0",
                                "$schema": "https://json.schemastore.org/sarif-2.1.0.json",
                                "runs": [{
                                    "tool": {
                                        "driver": {
                                            "name": "OWASP ZAP",
                                            "informationUri": "https://www.zaproxy.org/",
                                            "version": "2.12.0",
                                            "rules": []
                                        }
                                    },
                                    "results": []
                                }]
                            }

                            alerts = zap_data.get("site", [])[0].get("alerts", [])  # Assuming alerts are in the first site (http://juice-shop.herokuapp.com)
                            severity_map = {"High": "error", "Medium": "warning", "Low": "note", "Informational": "none"}

                            # Add alerts to the SARIF report
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
                                    sarif_report["runs"][0]["results"].append({
                                        "ruleId": rule_id,
                                        "level": severity,
                                        "message": {"text": alert.get("desc", "No description available.")},
                                        "locations": [{
                                            "physicalLocation": {
                                                "artifactLocation": {"uri": instance.get("uri", "Unknown")},
                                                "region": {"startLine": 1}
                                            }
                                        }]
                                    })

                        # Clean the SARIF report by removing <p> tags
                        sarif_report = remove_p_tags(sarif_report)
                        
                        # Write the cleaned SARIF report to file
                        with open(SARIF_FILE, "w") as sarif_file:
                            json.dump(sarif_report, sarif_file, indent=4)
                        
                        print(f"SARIF report generated and cleaned: {SARIF_FILE}")

