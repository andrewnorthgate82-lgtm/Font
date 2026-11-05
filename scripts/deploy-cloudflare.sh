#!/usr/bin/env bash
# Manual, idempotent deployment for the reviewed GitHub Actions workflow.
# No Telegram post/message is sent by this script.
set -euo pipefail
export CI=1

WORKER_NAME='nasra-esfahan-media-studio'
PAGES_PROJECT='nasra-esfahan-media-studio'
DB_NAME='nasra_esfahan'
CONFIG='wrangler.generated.toml'
PUBLIC_DIR='.deploy-public'
TEMP_DIR="${RUNNER_TEMP:-${TMPDIR:-/tmp}}"
WRANGLER=(npx --yes "wrangler@${WRANGLER_VERSION:-4.157.0}")
cleanup() { rm -f "$CONFIG"; rm -rf "$PUBLIC_DIR"; }
trap cleanup EXIT
cleanup

: "${CLOUDFLARE_API_TOKEN:?Add CLOUDFLARE_API_TOKEN in GitHub Actions secrets}"
: "${CLOUDFLARE_ACCOUNT_ID:?Add CLOUDFLARE_ACCOUNT_ID in GitHub Actions secrets}"
: "${OWNER_PASSWORD:?Add OWNER_PASSWORD in GitHub Actions secrets}"
for command in node npx openssl curl; do command -v "$command" >/dev/null || { echo "Missing command: $command"; exit 1; }; done

find_database_id() {
  "${WRANGLER[@]}" d1 list --json | DB_NAME="$DB_NAME" node --input-type=module -e '
    let text=""; for await (const chunk of process.stdin) text+=chunk;
    const parsed=JSON.parse(text); const rows=Array.isArray(parsed)?parsed:(parsed.databases||parsed.result||[]);
    const row=rows.find(x=>x.name===process.env.DB_NAME);
    if(row) process.stdout.write(String(row.uuid||row.id||row.database_id||""));
  '
}

echo 'Checking the D1 database…'
DB_ID="$(find_database_id)"
if [[ -z "$DB_ID" ]]; then
  "${WRANGLER[@]}" d1 create "$DB_NAME"
  DB_ID="$(find_database_id)"
fi
[[ "$DB_ID" =~ ^[0-9a-fA-F-]{32,40}$ ]] || { echo 'Cloudflare did not return a valid D1 database ID'; exit 1; }
cp wrangler.toml "$CONFIG"
DB_ID="$DB_ID" node --input-type=module -e '
  import fs from "node:fs";
  let text=fs.readFileSync("wrangler.generated.toml","utf8");
  text=text.replace(/database_id\s*=\s*"[^"]+"/,`database_id = "${process.env.DB_ID}"`);
  fs.writeFileSync("wrangler.generated.toml",text);
'

echo 'Applying the idempotent D1 schema…'
"${WRANGLER[@]}" d1 execute "$DB_NAME" --remote --file schema.sql --config "$CONFIG" --yes

echo 'Deploying the Worker…'
"${WRANGLER[@]}" deploy --config "$CONFIG" 2>&1 | tee "$TEMP_DIR/nasra-worker-deploy.log"
WORKER_URL="$(sed -r 's/\x1B\[[0-9;]*[mK]//g' "$TEMP_DIR/nasra-worker-deploy.log" | grep -oE "https://${WORKER_NAME}\.[A-Za-z0-9-]+\.workers\.dev" | head -n 1 || true)"
[[ -n "$WORKER_URL" ]] || { echo 'Worker deployed but its workers.dev URL was not found'; exit 1; }

put_secret() {
  local name="$1" value="$2"
  [[ -n "$value" ]] || return 0
  printf '%s' "$value" | "${WRANGLER[@]}" secret put "$name" --config "$CONFIG" >/dev/null
}
secret_exists() {
  local listing
  listing="$("${WRANGLER[@]}" secret list --config "$CONFIG" --format json)"
  SECRET_NAME="$1" node --input-type=module -e '
    let text=""; for await (const chunk of process.stdin) text+=chunk;
    const names=new Set();
    function walk(value){
      if(Array.isArray(value)){ value.forEach(walk); return; }
      if(value&&typeof value==="object"){
        if(typeof value.name==="string") names.add(value.name);
        Object.values(value).forEach(walk);
      }
    }
    walk(JSON.parse(text)); process.exit(names.has(process.env.SECRET_NAME)?0:1);
  ' <<<"$listing"
}
ensure_random_secret() {
  local name="$1"
  if ! secret_exists "$name"; then
    openssl rand -hex 32 | "${WRANGLER[@]}" secret put "$name" --config "$CONFIG" >/dev/null
    echo "Created missing $name (value is not printed)."
  fi
}

echo 'Configuring Worker secrets without printing their values…'
put_secret OWNER_PASSWORD "$OWNER_PASSWORD"
ensure_random_secret SESSION_SECRET
TELEGRAM_CONFIGURED=0
if [[ -n "${TG_BOT_TOKEN:-}" ]]; then
  put_secret TG_BOT_TOKEN "$TG_BOT_TOKEN"
  ensure_random_secret TG_SECRET
  echo 'Verifying the Telegram bot, owner and test channel; sending nothing…'
  if secret_exists TG_OWNER_CHAT_ID && WORKER_URL="$WORKER_URL" node scripts/telegram-bootstrap.mjs has-channel; then
    WORKER_URL="$WORKER_URL" node scripts/telegram-bootstrap.mjs finalize-existing
  else
    node scripts/telegram-bootstrap.mjs discover "$TEMP_DIR/nasra-telegram-bootstrap.json"
    DISCOVERED_OWNER="$(node --input-type=module -e 'import fs from "node:fs";const x=JSON.parse(fs.readFileSync(process.argv[1],"utf8"));process.stdout.write(x.ownerChatId);' "$TEMP_DIR/nasra-telegram-bootstrap.json")"
    put_secret TG_OWNER_CHAT_ID "$DISCOVERED_OWNER"
    WORKER_URL="$WORKER_URL" node scripts/telegram-bootstrap.mjs finalize "$TEMP_DIR/nasra-telegram-bootstrap.json"
  fi
  TELEGRAM_CONFIGURED=1
fi

echo 'Preparing the public frontend with only the non-secret Worker origin…'
rm -rf "$PUBLIC_DIR"
cp -R public "$PUBLIC_DIR"
WORKER_URL="$WORKER_URL" node --input-type=module -e '
  import fs from "node:fs";
  const text="// Public API origin only; generated during deployment. No secrets.\nexport const API_BASE = "+JSON.stringify(process.env.WORKER_URL)+";\n";
  fs.writeFileSync(".deploy-public/js/config.js",text);
'
PAGES_LIST="$("${WRANGLER[@]}" pages project list --json)"
if ! PAGES_PROJECT="$PAGES_PROJECT" node --input-type=module -e '
  let text=""; for await (const chunk of process.stdin) text+=chunk;
  const parsed=JSON.parse(text); const rows=Array.isArray(parsed)?parsed:(parsed.projects||parsed.result||[]);
  process.exit(rows.some(x=>x.name===process.env.PAGES_PROJECT)?0:1);
' <<<"$PAGES_LIST"; then
  "${WRANGLER[@]}" pages project create "$PAGES_PROJECT" --production-branch main
fi
"${WRANGLER[@]}" pages deploy "$PUBLIC_DIR" --project-name "$PAGES_PROJECT" --branch main --commit-hash "${GITHUB_SHA:-local}" --commit-dirty false

echo 'Running read-only health checks…'
curl -fsS --max-time 30 "$WORKER_URL/api/health" | node --input-type=module -e '
  let text=""; for await (const chunk of process.stdin) text+=chunk;
  const data=JSON.parse(text); if(!data.ok||!data.db) process.exit(1);
  console.log(`Worker health: ok; D1: connected; version: ${data.version}`);
'
PAGES_URL="https://${PAGES_PROJECT}.pages.dev"
PAGES_OK=0
for attempt in 1 2 3 4 5 6; do
  if curl -fsS --max-time 30 "$PAGES_URL/" -o "$TEMP_DIR/nasra-pages.html"; then PAGES_OK=1; break; fi
  sleep 10
done
[[ "$PAGES_OK" == 1 ]] || { echo 'Pages deployment succeeded but the public health check did not become ready'; exit 1; }
grep -q 'نسرا' "$TEMP_DIR/nasra-pages.html"

if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
  { echo "worker_url=$WORKER_URL"; echo "pages_url=$PAGES_URL"; echo "telegram_configured=$TELEGRAM_CONFIGURED"; } >> "$GITHUB_OUTPUT"
fi
if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  {
    echo '### Nasra deployment complete'
    echo "- Worker API: $WORKER_URL"
    echo "- Frontend: $PAGES_URL"
    echo "- Telegram configured: $TELEGRAM_CONFIGURED"
    echo '- External posts sent: 0'
  } >> "$GITHUB_STEP_SUMMARY"
fi
echo "Deployment complete. Frontend: $PAGES_URL"
echo 'No Telegram post or message was sent.'
