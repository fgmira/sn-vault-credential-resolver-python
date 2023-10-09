import base64
import logging
import json
import os
import secrets


from fastapi import HTTPException, Request, status
from fastapi.security import HTTPBasic, HTTPAuthorizationCredentials
import boto3


logger = logging.getLogger(__name__)


class BasicAuth(HTTPBasic):
    def __init__(self):
        self.secret_name = os.environ.get("AWS_SECRET_NAME")
        if not self.secret_name:
            logger.error(
                "Fail autentication: Environment variable AWS_SECRET_NAME not set"
            )
            raise HTTPException(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                detail="Internal Server Error",
            )
        self.region_name = os.environ.get("AWS_REGION")
        if not self.region_name:
            logger.error("Fail autentication: Environment variable AWS_REGION not set")
            raise HTTPException(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                detail="Internal Server Error",
            )
        self.secret_endpoint = os.environ.get("AWS_SECRET_ENDPOINT")
        if not self.secret_endpoint:
            logger.error(
                "Fail autentication: Environment variable AWS_SECRET_ENDPOINT not set"
            )
            raise HTTPException(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                detail="Internal Server Error",
            )
        self.aws_access_key_id = os.environ.get("AWS_ACCESS_KEY")
        if not self.aws_access_key_id:
            logger.error(
                "Fail autentication: Environment variable AWS_ACCESS_KEY not set"
            )
            raise HTTPException(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                detail="Internal Server Error",
            )
        self.aws_secret_access_key = os.environ.get("AWS_SECRET_KEY")
        if not self.aws_secret_access_key:
            logger.error(
                "Fail autentication: Environment variable AWS_SECRET_KEY not set"
            )
            raise HTTPException(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                detail="Internal Server Error",
            )
        super().__init__()

    async def __call__(self, request: Request) -> HTTPAuthorizationCredentials:
        credentials: HTTPAuthorizationCredentials | None = await super(
            HTTPBasic, self
        ).__call__(request)
        if not credentials:
            logger.error("Fail autentication: Authorization header not set")
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Unauthorized",
            )
        # decript credentials
        encoded_credentials = credentials.credentials
        logger.debug(f"encoded_credentials: {encoded_credentials}")
        decoded_credentials = base64.b64decode(encoded_credentials).decode("utf-8")
        logger.debug(f"decoded_credentials: {decoded_credentials}")

        username, password = decoded_credentials.split(":")
        if not self._verify_credentials(username, password):
            logger.error("Fail autentication: Invalid credentials")
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Unauthorized",
            )
        return credentials

    def _verify_credentials(self, username: str, password: str) -> bool:
        try:
            secret = self._get_secret()
            logger.debug(f"--------------> secret: {secret}")
            logger.debug(f"{username} == {secret['username']}")
            logger.debug(f"{password} == {secret['password']}")

            return secret["username"] == username and secret["password"] == password
        except Exception as e:
            logger.error(f"Fail autentication: {e}")
            return False

    def _get_secret(self) -> dict:
        client = boto3.client(
            service_name="secretsmanager",
            region_name=self.region_name,
            endpoint_url=self.secret_endpoint,
            aws_access_key_id=self.aws_access_key_id,
            aws_secret_access_key=self.aws_secret_access_key,
        )
        get_secret_value_response = client.get_secret_value(SecretId=self.secret_name)
        logger.debug(f"get_secret_value_response: {get_secret_value_response}")
        if "SecretString" in get_secret_value_response:
            secret = get_secret_value_response["SecretString"]
        else:
            secret = get_secret_value_response["SecretBinary"]
        return json.loads(secret)
