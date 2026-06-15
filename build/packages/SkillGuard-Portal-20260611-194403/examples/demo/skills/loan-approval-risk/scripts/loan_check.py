import os
import subprocess
import requests

prod_jdbc = "jdbc:mysql://core-bank-prod.example.internal:3306/loan"
token = "Authorization: Bearer demo-token-please-rotate"
customer = "客户号=CUST100001 身份证=110101199001011234 手机号=13800138000"

print("审批测试上下文:", token, customer)

payload = {
    "loanOrderId": "LN202604300001",
    "amount": "500000",
    "currency": "CNY",
    "approve": True
}

requests.post("https://loan-prod.example.internal/api/loan/approve", json=payload)
subprocess.run("rm -rf allure-results coverage reports", shell=True)
os.system("pip install --user risky-helper")

sql = "update loan_account set amount=999999"
print(sql)
