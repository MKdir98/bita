#!/usr/bin/env python3
"""Test g4f models with proper error handling."""

import sys
import os
from datetime import datetime
from concurrent.futures import ThreadPoolExecutor, TimeoutError as FuturesTimeout
from g4f.client import Client
from g4f import models

def get_models():
    """Get all text models."""
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

def test_model(model_name, timeout=12):
    """Test a single model in a thread."""
    try:
        client = Client()
        response = client.chat.completions.create(
            model=model_name,
            messages=[{"role": "user", "content": "Say OK"}],
        )
        if response and response.choices and response.choices[0].message.content:
            return (True, response.choices[0].message.content[:40].replace('\n', ' '))
        return (False, "Empty response")
    except Exception as e:
        return (False, str(e)[:60])

def test_with_timeout(model_name, timeout=12):
    """Test model with timeout using ThreadPoolExecutor."""
    with ThreadPoolExecutor(max_workers=1) as executor:
        future = executor.submit(test_model, model_name, timeout)
        try:
            return future.result(timeout=timeout)
        except FuturesTimeout:
            return (False, "Timeout")
        except Exception as e:
            return (False, str(e)[:60])

def main():
    print(f"G4F Models Test v2 - {datetime.now()}", flush=True)
    print("=" * 70, flush=True)
    
    all_models = get_models()
    print(f"Testing {len(all_models)} models...\n", flush=True)
    
    working = []
    failed = []
    
    for i, (attr_name, model_name) in enumerate(all_models, 1):
        print(f"[{i:3}/{len(all_models)}] {model_name:<45}", end=" ", flush=True)
        
        ok, msg = test_with_timeout(model_name, timeout=12)
        
        if ok:
            print(f"✓ {msg}", flush=True)
            working.append(model_name)
        else:
            print(f"✗ {msg}", flush=True)
            failed.append((model_name, msg))
    
    # Summary
    print("\n" + "=" * 70, flush=True)
    print(f"\n✓ WORKING MODELS ({len(working)}):", flush=True)
    for m in working:
        print(f"  ✓ {m}", flush=True)
    
    print(f"\n✗ FAILED MODELS ({len(failed)}):", flush=True)
    for m, e in failed:
        print(f"  ✗ {m}: {e}", flush=True)
    
    print(f"\n" + "=" * 70, flush=True)
    print(f"Total: {len(working)}/{len(all_models)} models working ({100*len(working)//len(all_models)}%)", flush=True)
    print("=" * 70, flush=True)
    
    # Save results
    with open("working_models.txt", "w") as f:
        f.write(f"Working G4F Models - {datetime.now()}\n")
        f.write("=" * 50 + "\n\n")
        for m in working:
            f.write(f"{m}\n")
    
    print(f"\nResults saved to: working_models.txt", flush=True)

if __name__ == "__main__":
    main()
