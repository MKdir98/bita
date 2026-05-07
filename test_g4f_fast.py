#!/usr/bin/env python3
"""
Test all available g4f models concurrently to see which ones are working.
"""

import asyncio
import sys
from datetime import datetime
from g4f.client import AsyncClient
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

async def test_model_async(model_name, model_obj, semaphore, timeout=15):
    """Test a single model asynchronously with semaphore for rate limiting."""
    async with semaphore:
        client = AsyncClient()
        actual_name = model_obj.name if hasattr(model_obj, 'name') else model_name
        
        try:
            response = await asyncio.wait_for(
                client.chat.completions.create(
                    model=actual_name,
                    messages=[{"role": "user", "content": "Say 'OK' only."}],
                ),
                timeout=timeout
            )
            
            if response and response.choices and len(response.choices) > 0:
                content = response.choices[0].message.content
                if content and len(content.strip()) > 0:
                    return (model_name, actual_name, True, content[:50].replace('\n', ' '))
            return (model_name, actual_name, False, "Empty response")
        except asyncio.TimeoutError:
            return (model_name, actual_name, False, "Timeout")
        except Exception as e:
            error_msg = str(e)[:60].replace('\n', ' ')
            return (model_name, actual_name, False, f"{error_msg}")

async def main():
    print(f"=" * 70)
    print(f"G4F Models Test (Concurrent) - {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print(f"=" * 70)
    
    all_models = get_all_models()
    print(f"\nFound {len(all_models)} models to test\n")
    print("Testing all models concurrently (this may take 1-2 minutes)...\n")
    
    # Semaphore to limit concurrent requests (avoid overwhelming)
    semaphore = asyncio.Semaphore(20)
    
    # Create all tasks
    tasks = [
        test_model_async(name, model_obj, semaphore)
        for name, model_obj in all_models
    ]
    
    # Run all tasks concurrently
    results = await asyncio.gather(*tasks, return_exceptions=True)
    
    working = []
    failed = []
    
    for result in results:
        if isinstance(result, Exception):
            continue
        name, actual_name, success, msg = result
        if success:
            working.append((name, actual_name, msg))
        else:
            failed.append((name, actual_name, msg))
    
    # Summary
    print(f"{'=' * 70}")
    print(f"RESULTS")
    print(f"{'=' * 70}")
    
    print(f"\n✓ WORKING MODELS ({len(working)}):")
    print("-" * 70)
    for name, actual_name, response in sorted(working, key=lambda x: x[1]):
        print(f"  ✓ {actual_name:<40} | Response: {response}")
    
    print(f"\n✗ FAILED MODELS ({len(failed)}):")
    print("-" * 70)
    for name, actual_name, error in sorted(failed, key=lambda x: x[1]):
        print(f"  ✗ {actual_name:<40} | {error}")
    
    print(f"\n{'=' * 70}")
    print(f"SUMMARY: {len(working)}/{len(all_models)} models working ({len(working)*100//len(all_models)}%)")
    print(f"{'=' * 70}")
    
    # Save working models to file
    with open("working_models.txt", "w") as f:
        f.write(f"Working G4F Models - {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
        f.write("=" * 50 + "\n\n")
        for name, actual_name, response in sorted(working, key=lambda x: x[1]):
            f.write(f"{actual_name}\n")
    
    print(f"\nWorking models saved to: working_models.txt")

if __name__ == "__main__":
    asyncio.run(main())
