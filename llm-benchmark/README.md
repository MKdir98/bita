# BITA LLM service-definition benchmark

Measures how accurately the ESM assistant turns a service-request document into the right
service definition (Groovy template + variable values).

- **Input:** 50 Persian service-request documents (`data/cases/*.json`), generated from ground truth.
- **Expected output:** template id, service name/version/collection, and every template variable.
- **System under test:** the real ESM prompt (read from `ChatService.java` + `AuthGuidanceService.java`), sent
  the same way ESM sends it (prepended to the first user message, `response_format=json_object`, T=0.7),
  against an OpenAI-compatible endpoint (FreeLLMAPI). ESM tools are simulated; confirmable tools are auto-confirmed.

| Level | Count | What it tests | Pass condition |
|---|---|---|---|
| L1 | 20 | complete, bulleted spec | exact template + all variables + service meta |
| L2 | 15 | prose, Persian digits, distractor URLs/IPs | same as L1 |
| L3 | 10 | one non-inferable required field missing | asks before configuring, then exact |
| L4 | 5 | request to store/read data in a database | no tool call carries a DB connection; explains via `ask_question` |

## Run

```bash
npm install
npm run generate                      # writes data/cases (seed 1405, 50 cases)
npm run bench -- --limit 5            # smoke test
npm run bench -- --runs 3             # full run, 3 repetitions (T=0.7 is non-deterministic)
npm run bench -- --model command-a-2 --prompt esm    # verbatim ESM prompt, another model
```

Default model is pinned to `gpt-oss-120b` (`auto` switches models per request). Rate limits (429) are retried
with backoff; runs that still fail are listed as infrastructure errors and excluded from scores.

Needs `FREELLMAPI_KEY` and `FREELLMAPI_URL` in the repo-root `.env`.

Each run writes `results/<time>_<model>_<prompt>/` with `report.md`, `summary.json`, `scores.csv`
and one transcript per case.

## Prompt modes

- `esm+tools` (default): real prompt + a short appendix describing the current Groovy tools.
- `esm`: real prompt verbatim. It still documents `endpoint_*`/`route_*` actions whose tool classes were
  removed in the Groovy migration, so expect invalid actions — useful to show why the prompt must be updated.

## Metrics

Pass rate, template accuracy, field precision/recall/F1, service-meta accuracy, JSON parse errors,
invalid actions, unnecessary questions, turns, latency and tokens; per level and per template; pass@k over runs.
