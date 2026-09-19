# OpenRouter API Key Status Report

A Java command-line tool to query your OpenRouter API key status and account information.

## What It Does

This tool calls two OpenRouter API endpoints to fetch and display:
- **Key metadata** — label, key type (management/provisioning), tier status
- **Rate limits** — current limit, remaining allowance, usage tracking
- **Usage** — total, daily, weekly, and monthly spending
- **Free-tier allowance** — daily free requests (if applicable)
- **Account balance** — credit balance (management keys only)

The output is formatted as an aligned, human-readable report. Optionally, dump the raw merged JSON with `--json`.

## Requirements

- Java 21+
- Maven 3.9+
- OpenRouter API key in `.env` file or `OPENROUTER_API_KEY` environment variable

## Setup

1. Create a `.env` file in the project root:
   ```bash
   echo "OPENROUTER_API_KEY=sk-or-v1-..." > .env
   ```

   Or set the environment variable:
   ```bash
   export OPENROUTER_API_KEY="sk-or-v1-..."
   ```

2. Build the project:
   ```bash
   mvn -q compile && mvn -q package
   ```

## Usage

Run the compiled JAR:

```bash
# Normal report format (human-readable)
java -jar target/api-client.jar

# JSON output (raw data)
java -jar target/api-client.jar --json
```

## Output Examples

### Report Format (default)
```
Key Identity
  sk-or-v1-0d5...ece

Limits and Balance
  Rate Limit: 40/min
  Limit Remaining: $39.7183
  Limit Reset: —
  Account Balance: (management key required)

Usage
  Total: $0.2817
  Daily: $0.2817
  Weekly: $0.2817
  Monthly: $0.2817

Free-tier Allowance
  Free Tier: false
  Free Daily Used: 53
  Free Daily Limit: 1000
  Free Daily Remaining: 947

Account Credits
  Management Key: false
  Provisioning Key: false
  Expires At: —
  Allowed Data Regions: ["global"]
```

### JSON Output (`--json`)
Raw merged JSON from both API endpoints:
```json
{
  "label": "sk-or-v1-0d5...ece",
  "is_management_key": false,
  "usage": 0.28167855,
  "usage_daily": 0.28167855,
  ...
}
```

## Exit Codes

- `0` — Success
- `1` — Configuration error (missing API key)
- `2` — API failure (401, 403, 429, 5xx)

## Error Messages

| Status | Message |
|--------|---------|
| 401 | Authentication failed: Invalid or expired API key |
| 403 | Access denied: Insufficient permissions for this operation |
| 429 | Rate limited: Too many requests, please retry later |
| 5xx | Server error: OpenRouter API is experiencing issues |

**Note:** A 403 from the `/credits` endpoint is expected for non-management keys and displays as "(management key required)" in the report, not an error.

## Implementation Notes

- Uses Java 21 and Maven with no external frameworks
- Built-in `java.net.http.HttpClient` (no third-party HTTP library)
- 15-second request timeout per endpoint
- API keys are never printed to stdout
- Null/missing fields display as em dashes (`—`) in the report
- Money values always format to 4 decimal places as `$X.XXXX`
