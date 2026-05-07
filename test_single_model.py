#!/usr/bin/env python3
"""Test a single g4f model."""
import sys
from g4f.client import Client

def main():
    if len(sys.argv) < 2:
        print("Usage: test_single_model.py <model_name>")
        sys.exit(1)
    
    model_name = sys.argv[1]
    
    try:
        client = Client()
        response = client.chat.completions.create(
            model=model_name,
            messages=[{"role": "user", "content": "Say OK"}],
        )
        if response and response.choices and response.choices[0].message.content:
            content = response.choices[0].message.content[:50].replace('\n', ' ')
            print(f"OK:{content}")
        else:
            print("FAIL:Empty response")
    except Exception as e:
        print(f"FAIL:{str(e)[:80]}")

if __name__ == "__main__":
    main()
