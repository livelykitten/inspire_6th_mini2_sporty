#!/usr/bin/env python3
"""Run W-32 Kafka/MariaDB tests using a disposable DB/account; never touch user_db."""
from pathlib import Path
import os
import secrets
import subprocess
import sys
import uuid


ROOT = Path(__file__).resolve().parents[2]


def sql(statement):
    # The root password is expanded inside the container, never returned to the host/log.
    subprocess.run(
        ["docker", "compose", "exec", "-T", "match-db", "sh", "-c",
         'exec mariadb -uroot -p"$MARIADB_ROOT_PASSWORD"'],
        input=statement.encode("utf-8"), cwd=ROOT, check=True,
    )


def main():
    name = "w32_test_" + uuid.uuid4().hex[:12]
    password = secrets.token_hex(24)
    backend = ROOT / "backend"
    try:
        sql(f"CREATE DATABASE `{name}`;\n"
            f"CREATE USER '{name}'@'%' IDENTIFIED BY '{password}';\n"
            f"GRANT ALL PRIVILEGES ON `{name}`.* TO '{name}'@'%';\n")
        env = os.environ.copy()
        env.update({
            "W32_EXTERNAL_TESTS": "true",
            "W32_JDBC_URL": f"jdbc:mariadb://localhost:3307/{name}",
            "W32_JDBC_USER": name,
            "W32_JDBC_PASSWORD": password,
        })
        command = (["cmd", "/c", str(backend / "gradlew.bat")]
                   if os.name == "nt" else [str(backend / "gradlew")])
        command += [":match-service:test", "--tests", "*WithdrawalExternalIntegrationTest",
                    "--rerun-tasks", "--console=plain"]
        return subprocess.run(command, cwd=backend, env=env).returncode
    finally:
        # These identifiers were generated above and refer only to this test invocation.
        sql(f"DROP DATABASE IF EXISTS `{name}`; DROP USER IF EXISTS '{name}'@'%';\n")


if __name__ == "__main__":
    sys.exit(main())
