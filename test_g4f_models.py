#!/usr/bin/env python3
"""
Test all available g4f models to see which ones are working.
"""

import asyncio
import sys
from datetime import datetime
from g4f.client import Client, AsyncClient
from g4f import models

# Get all model objects (filter out non-model attributes)
def get_all_models():
    """Get all available text models from g4f."""
    all_models = []
    
    for name in dir(models):
        if name.startswith('_'):
            continue
        
        obj = getattr(models, name)
        
        # Check if it's a Model instance
        if hasattr(obj, 'name') and hasattr(obj, 'base_provider'):
            # Skip image/audio/video models
            if isinstance(obj, (models.ImageModel, models.AudioModel, models.VideoModel)):
                continue
            all_models.append((name, obj))
    
    return all_models

async def test_model_async(client, model_name, model_obj, timeout=30):
    """Test a single model asynchronously."""
    try:
        response = await asyncio.wait_for(
            client.chat.completions.create(
                model=model_obj.name if hasattr(model_obj, 'name') else model_name,
                messages=[{"role": "user", "content": "Say 'Hello' in one word only."}],
                timeout=timeout
            ),
            timeout=timeout + 5
        )
        
        if response and response.choices and len(response.choices) > 0:
            content = response.choices[0].message.content
            if content and len(content.strip()) > 0:
                return True, content[:100].replace('\n', ' ')
        return False, "Empty response"
    except asyncio.TimeoutError:
        return False, "Timeout"
    except Exception as e:
        error_msg = str(e)[:80].replace('\n', ' ')
        return False, f"Error: {error_msg}"

async def main():
    print(f"=" * 70)
    print(f"G4F Models Test - {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print(f"=" * 70)
    
    all_models = get_all_models()
    print(f"\nFound {len(all_models)} models to test\n")
    
    working = []
    failed = []
    
    client = AsyncClient()
    
    # Test each model
    for i, (name, model_obj) in enumerate(all_models, 1):
        model_name = model_obj.name if hasattr(model_obj, 'name') else name
        print(f"[{i}/{len(all_models)}] Testing {model_name}...", end=" ", flush=True)
        
        success, result = await test_model_async(client, name, model_obj, timeout=20)
        
        if success:
            print(f"✓ OK - {result}")
            working.append((name, model_name, result))
        else:
            print(f"✗ FAIL - {result}")
            failed.append((name, model_name, result))
    
    # Summary
    print(f"\n{'=' * 70}")
    print(f"SUMMARY")
    print(f"{'=' * 70}")
    print(f"\n✓ Working models ({len(working)}):")
    for name, model_name, response in working:
        print(f"  - {model_name}")
    
    print(f"\n✗ Failed models ({len(failed)}):")
    for name, model_name, error in failed:
        print(f"  - {model_name}: {error}")
    
    print(f"\n{'=' * 70}")
    print(f"Total: {len(working)}/{len(all_models)} models working")
    print(f"{'=' * 70}")

if __name__ == "__main__":
    asyncio.run(main())
