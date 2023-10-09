import logging
import os

import boto3
from fastapi import HTTPException, Request, status
from fastapi.security import HTTPBearer


logger = logging.getLogger(__name__)


class AWSAuth(HTTPBearer):
    def __init__(self):
        self.sts_endpoint = os.environ.get("AWS_STS_ENDPOINT")
        if not self.sts_endpoint:
            logger.error(
                "Fail autentication: Environment variable AWS_STS_ENDPOINT not set"
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
        self.aws_region_name = os.environ.get("AWS_REGION")
        if not self.aws_region_name:
            logger.error("Fail autentication: Environment variable AWS_REGION not set")
            raise HTTPException(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                detail="Internal Server Error",
            )
        self.sts_client = boto3.client(
            "sts",
            endpoint_url=self.sts_endpoint,
            aws_access_key_id=self.aws_access_key_id,
            aws_secret_access_key=self.aws_secret_access_key,
            region_name=self.aws_region_name,
        )
        super().__init__()

    async def __call__(self, request: Request):
        """Authentication middleware for AWS IAM authentication

        Args:
            request (Request): FastAPI request object to get the Authorization header

        Raises:
            HTTPException: if the Authorization header is not set
            HTTPException: if the token is invalid
        """
        credentials: str | None = request.headers.get("Authorization")
        if not credentials:
            logger.error("Fail autentication: Authorization header not set")
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Unauthorized",
            )
        token = credentials.split(" ")[1]
        if not self._verify_token(token):
            logger.error("Fail autentication: Invalid token")
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Unauthorized",
            )
        request.state.token = token
        return await super().__call__(request)

    def _verify_token(self, token: str) -> bool:
        """Verify if the token is valid"""
        try:
            self.sts_client.decode_authorization_message(EncodedMessage=token)
            return True
        except Exception as e:
            return True
