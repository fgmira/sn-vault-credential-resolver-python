FROM python:3.10-bookworm

COPY asset/* opt/credential_resolver_py/

WORKDIR /opt/credential_resolver_py

RUN pip install -r requirements.txt
RUN ls -la

ENV VAULT_ADDR ""\
    VAULT_TOKEN ""\
    LOG_LEVEL ""\
    AWS_STS_ENDPOINT ""\
    AWS_SECRET_ENDPOINT ""\
    AWS_SECRET_NAME ""\
    AWS_REGION ""\
    AWS_ACCESS_KEY ""\
    AWS_SECRET_KEY ""

EXPOSE 8000:8000

ENTRYPOINT [ "uvicorn",  "main:app", "--reload", "--host", "0.0.0.0", "--port", "8000"]


