import json

with open('/data/data/com.termux/files/home/.gemini/antigravity-cli/brain/fed67668-23ff-4e06-a55c-e42bb7a598e8/.system_generated/logs/transcript_full.jsonl') as f:
    for line in f:
        data = json.loads(line)
        if 'tool_calls' in data:
            for tc in data['tool_calls']:
                if tc['name'] == 'replace_file_content':
                    if 'MainActivity.kt' in tc['args'].get('TargetFile', ''):
                        print("==== REPLACEMENT ====")
                        print("StartLine:", tc['args'].get('StartLine'))
                        print("EndLine:", tc['args'].get('EndLine'))
                        print("TargetContent:", repr(tc['args'].get('TargetContent')))
                        print("ReplacementContent:", repr(tc['args'].get('ReplacementContent')))
                        print("==================\n")
