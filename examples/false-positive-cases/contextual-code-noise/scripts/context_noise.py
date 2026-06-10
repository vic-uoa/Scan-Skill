import os
import re


def auth_headers(resp_data):
    if not resp_data.get("access_token"):
        raise RuntimeError("access_token not found in response")
    return {
        "Content-Type": "application/json",
        "Authorization": f"Bearer {resp_data['access_token']}",
    }


def env_config():
    token = os.environ.get("ID_TOKEN", "")
    api_key = os.environ.get("CLAW_PARTNER_TOKEN", "")
    return {"token": token, "api_key": api_key}


def parse_secret_key_reference(entry):
    key_lower = entry.key.lower()
    if "entry.key" in key_lower or ".key" in key_lower:
        return entry.valueFrom.secretKeyRef.key
    return None


def scanner_pattern_only(value_upper):
    return re.match(r"^[A-Z][A-Z0-9\-]*-[A-Z]+", value_upper)


def placeholder_connection(user, password, host, port, database):
    postgres_url = f"postgresql://{user}:{password}@{host}:{port}/{database}"
    mysql_url = "mysql://{user}:{password}@{host}:{port}/{database}"
    client_secret = "my-client-secret"
    return postgres_url, mysql_url, client_secret


def runtime_connection(args, config):
    conn = (
        f"mssql+pyodbc://{args.user}:{args.password}@"
        f"{config.host}:{config.port}/{config.database}"
    )
    auth = {"Authorization": f"Bearer {config.access_token}"}
    return conn, auth


def rule_engine_noise(value):
    secret_patterns = [
        r"password\s*=\s*(.+)",
        r"access_token\s*:\s*(.+)",
        r"secretKeyRef\.key",
    ]
    return [re.compile(pattern).search(value) for pattern in secret_patterns]


def generic_business_label():
    """交易流水"""
    return {"comment": "交易流水", "label": "transaction flow"}
