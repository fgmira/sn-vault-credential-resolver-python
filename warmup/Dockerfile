FROM alpine:latest

RUN apk update && \
    apk add --no-cache python3 py3-pip && \
    pip3 install --upgrade pip && \
    pip3 install awscli

ENV AWS_ACCESS_KEY_ID="" \
    AWS_SECRET_ACCESS_KEY="" \
    AWS_DEFAULT_REGION="" \
    AWS_ENDPOINT_URL="" \
    AWS_SECRET_NAME="" \
    SECRET_USER_NAME="" \
    SECRET_USER_PWD="" \
    AWS_ACCOUNT_ID="" \
    AWS_ROLE_NAME="" 

COPY asset/* /opt/warmup/

RUN chmod 6750 /opt/warmup/*.sh

ENTRYPOINT ["sh", "-c", "/opt/warmup/warmup.sh"]
