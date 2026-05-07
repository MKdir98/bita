#!/usr/bin/env python3
"""Test all g4f models using subprocess with timeout."""

import subprocess
import sys
from datetime import datetime
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
            all_models.append(obj.name if hasattr(obj, 'name') else name)
    return list(set(all_models))  # Remove duplicates

def test_model(model_name, timeout=15):
    """Test a model using subprocess."""
    try:
        result = subprocess.run(
            [sys.executable, "test_single_model.py", model_name],
            capture_output=True,
            text=True,
            timeout=timeout
        )
        output = result.stdout.strip()
        if output.startswith("OK:"):
            return (True, output[3:])
        elif output.startswith("FAIL:"):
            return (False, output[5:])
        else:
            return (False, f"Unexpected: {output[:50]}")
    except subprocess.TimeoutExpired:
        return (False, "Timeout")
    except Exception as e:
        return (False, str(e)[:50])

def main():
    print(f"G4F Models Test - {datetime.now()}", flush=True)
    print("=" * 70, flush=True)
    
    all_models = sorted(get_models())
    print(f"Testing {len(all_models)} unique models...\n", flush=True)
    
    working = []
    failed = []
    
    for i, model_name in enumerate(all_models, 1):
        print(f"[{i:3}/{len(all_models)}] {model_name:<45}", end=" ", flush=True)
        
        ok, msg = test_model(model_name)
        
        if ok:
            print(f"✓ {msg}", flush=True)
            working.append(model_name)
        else:
            print(f"✗ {msg}", flush=True)
            failed.append((model_name, msg))
    
    # Summary
    print("\n" + "=" * 70, flush=True)
    print(f"\n✓ WORKING MODELS ({len(working)}):", flush=True)
    for m in sorted(working):
        print(f"  ✓ {m}", flush=True)
    
    print(f"\n✗ FAILED MODELS ({len(failed)}):", flush=True)
    for m, e in sorted(failed):
        print(f"  ✗ {m}: {e}", flush=True)
    
    print(f"\n" + "=" * 70, flush=True)
    print(f"TOTAL: {len(working)}/{len(all_models)} models working ({100*len(working)//len(all_models) if all_models else 0}%)", flush=True)
    print("=" * 70, flush=True)
    
    # Save results
    with open("working_models.txt", "w") as f:
        f.write(f"Working G4F Models - {datetime.now()}\n")
        f.write("=" * 50 + "\n\n")
        for m in sorted(working):
            f.write(f"{m}\n")
    
    print(f"\nResults saved to: working_models.txt", flush=True)

if __name__ == "__main__":
    main()
