#!/usr/bin/env python3
"""Test g4f models one by one."""

import sys
from datetime import datetime
from g4f.client import Client
from g4f import models
import signal

# Set timeout handler
class TimeoutError(Exception):
    pass

def timeout_handler(signum, frame):
    raise TimeoutError("Timeout")

# Get all text models
def get_models():
    all_models = []
    for name in dir(models):
        if name.startswith('_'):
            continue
        obj = getattr(models, name)
        if hasattr(obj, 'name') and hasattr(obj, 'base_provider'):
            if isinstance(obj, (models.ImageModel, models.AudioModel, models.VideoModel)):
                continue
            all_models.append((name, obj.name if hasattr(obj, 'name') else name))
    return all_models

def test_model(client, model_name):
    """Test a single model."""
    try:
        signal.signal(signal.SIGALRM, timeout_handler)
        signal.alarm(15)  # 15 second timeout
        
        response = client.chat.completions.create(
            model=model_name,
            messages=[{"role": "user", "content": "Say OK"}],
        )
        
        signal.alarm(0)  # Cancel timeout
        
        if response and response.choices and response.choices[0].message.content:
            return True, response.choices[0].message.content[:40].replace('\n', ' ')
        return False, "Empty"
    except TimeoutError:
        return False, "Timeout"
    except Exception as e:
        return False, str(e)[:50]
    finally:
        signal.alarm(0)

def main():
    print(f"G4F Models Test - {datetime.now()}", flush=True)
    print("=" * 60, flush=True)
    
    all_models = get_models()
    print(f"Testing {len(all_models)} models...\n", flush=True)
    
    working = []
    failed = []
    client = Client()
    
    for i, (attr_name, model_name) in enumerate(all_models, 1):
        print(f"[{i:3}/{len(all_models)}] {model_name:<40}", end=" ", flush=True)
        
        ok, msg = test_model(client, model_name)
        
        if ok:
            print(f"✓ {msg}", flush=True)
            working.append(model_name)
        else:
            print(f"✗ {msg}", flush=True)
            failed.append((model_name, msg))
    
    print("\n" + "=" * 60, flush=True)
    print(f"WORKING ({len(working)}):", flush=True)
    for m in working:
        print(f"  ✓ {m}", flush=True)
    
    print(f"\nFAILED ({len(failed)}):", flush=True)
    for m, e in failed:
        print(f"  ✗ {m}: {e}", flush=True)
    
    print(f"\nTotal: {len(working)}/{len(all_models)} working", flush=True)
    
    with open("working_models.txt", "w") as f:
        for m in working:
            f.write(f"{m}\n")

if __name__ == "__main__":
    main()
