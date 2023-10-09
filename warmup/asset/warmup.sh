# !/bin/bash

logInfo () {
  msg="[----> WARMUP <----] ${1}"
  echo "$msg" | tee -a ${LOG_FILE}
}

ValidateEnvVariables () {
  if [ -z "${AWS_DEFAULT_REGION}" ]; then
    logInfo "AWS_DEFAULT_REGION is not set"
    exit 1
  fi

  if [ -z "${AWS_ACCOUNT_ID}" ]; then
    logInfo "AWS_ACCOUNT_ID is not set"
    exit 1
  fi

  if [ -z "${AWS_SECRET_NAME}" ]; then
    logInfo "AWS_SECRET_NAME is not set"
    exit 1
  fi

  if [ -z "${SECRET_USER_NAME}" ]; then
    logInfo "SECRET_USER_NAME is not set"
    exit 1
  fi

  if [ -z "${SECRET_USER_PWD}" ]; then
    logInfo "SECRET_USER_PWD is not set"
    exit 1
  fi

  if [ -z "${AWS_ROLE_NAME}" ]; then
    logInfo "AWS_ROLE_NAME is not set"
    exit 1
  fi

  if [ -z "${AWS_ACCESS_KEY_ID}" ]; then
    logInfo "AWS_ACCESS_KEY_ID is not set"
    exit 1
  fi

  if [ -z "${AWS_SECRET_ACCESS_KEY}" ]; then
    logInfo "AWS_SECRET_ACCESS_KEY is not set"
    exit 1
  fi

  if [ -z "${AWS_ENDPOINT_URL}" ]; then
    logInfo "AWS_ENDPOINT_URL is not set"
    exit 1
  fi
}

CreateAWSResources(){
    logInfo "Creating AWS resources"
    # Create IAM role
    aws iam create-role --role-name ${AWS_ROLE_NAME} --assume-role-policy-document file://opt/warmup/assume-role-policy.json
    if [ $? -ne 0 ]; then
        logInfo "Failed to create IAM role"
        exit 1
    fi
    # Create secret in Secrets Manager
    fillSecretFile
    aws secretsmanager create-secret --name ${AWS_SECRET_NAME} --secret-string 'file://opt/warmup/secret.json'
    if [ $? -ne 0 ]; then
        logInfo "Failed to create secret in Secrets Manager"
        exit 1
    fi
}
fillSecretFile(){
  sed -i "s~SECRET_USER_NAME~${SECRET_USER_NAME}~g" /opt/warmup/secret.json
  sed -i "s~SECRET_USER_PWD~${SECRET_USER_PWD}~g" /opt/warmup/secret.json
}

logInfo "Starting warmup script"
ValidateEnvVariables
logInfo "All environment variables are set. Waiting for 20 seconds to localstack to be ready"
sleep 20

CreateAWSResources
logInfo "Completed warmup script"
exit 0


