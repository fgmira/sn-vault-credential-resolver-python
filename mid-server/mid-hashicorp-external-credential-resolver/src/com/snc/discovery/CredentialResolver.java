package com.snc.discovery;

import com.snc.automation_common.integration.creds.IExternalCredential;
import com.snc.core_automation_common.logging.Logger;
import com.snc.core_automation_common.logging.LoggerFactory;
import com.google.gson.Gson;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.AwsClient;
import software.amazon.awssdk.awscore.client.builder.AwsDefaultClientBuilder;
import software.amazon.awssdk.core.client.builder.SdkClientBuilder;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
/**
 * Custom External Credential Resolver for HashiCorp credential vault.
 * Use Vault Java Driver a community written zero-dependency
 * Java client from <a href="https://bettercloud.github.io/vault-java-driver/">...</a>
 */
public class CredentialResolver implements IExternalCredential{

	public static final String CREDENTIAL_RESOLVER_PYTHON_HOST = "ext.cred.credential.resolver.python.host";
	private static final String PYTHON_API_URL = "http://%s:8000/api/v1/token_login/credential/";
	//Remove hard-coded values and read them from config.xml
	private String credentialResolverPythonHost = "";

	// Logger object to log messages in agent.log
	private static final Logger fLogger = LoggerFactory.getLogger(CredentialResolver.class);

	public CredentialResolver() {
	}

	/**
	 * Config method with preloaded config parameters from config.xml.
	 * @param configMap - contains config parameters with prefix "ext.cred" only.
	 */
	@Override
	public void config(Map<String, String> configMap) {
		//Note: To load config parameters from MID config.xml if not available in configMap.
		//propValue = Config.get().getProperty("<Parameter Name>")

		// Load config parameters
		credentialResolverPythonHost = configMap.get(CREDENTIAL_RESOLVER_PYTHON_HOST);
		fLogger.info("[Vault] INFO - CredentialResolver: " +
				CREDENTIAL_RESOLVER_PYTHON_HOST + " = " + credentialResolverPythonHost);
		if (isNullOrEmpty(credentialResolverPythonHost)) {
			fLogger.error("[Vault] ERROR - CredentialResolver: " + CREDENTIAL_RESOLVER_PYTHON_HOST + " not set!");
			throw new RuntimeException("CredentialResolver: Property " + CREDENTIAL_RESOLVER_PYTHON_HOST
					+ " not set in config.xml!");
		}
	}
	/**
	 * Resolve a credential.
	 */
	@Override
	public Map<String, String> resolve(Map<String, String> args) {

		String credId = args.get(ARG_ID);
		String credType = args.get(ARG_TYPE);

		String username;
		String password = "";
		String passphrase = "";
		String private_key = "";

		Map<String, String> credential;

		fLogger.info("[Vault] INFO - CredentialResolver: Credential ID = " + credId);
		fLogger.info("[Vault] INFO - CredentialResolver: Credential Type = " + credType);
		if(credId == null || credType == null) {
			fLogger.error("[Vault] ERROR - CredentialResolver: Credential ID or Credential Type is null.");
			throw new RuntimeException("CredentialResolver: Credential ID or Credential Type is null.");
		}

		// Get credential with credential resolver python api
		String formattedUrl = String.format(PYTHON_API_URL, credentialResolverPythonHost);
		// Format JSON request body
		String jsonRequestBody = "{\"credential_id\": \"" + credId + "\", \"role\": \"test\"}";
		byte[] jsonRequestBodyBytes = jsonRequestBody.getBytes(StandardCharsets.UTF_8);
		// Call credential resolver python api

		try {
			Gson gson = new Gson();
			URL url = new URL(formattedUrl);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("POST");
			conn.setRequestProperty("Content-Type", "application/json");
			conn.setRequestProperty("Authorization", "Basic " + new String(getCredentialToApi()));
			conn.setDoOutput(true);
			conn.setRequestProperty("Content-Length", Integer.toString(jsonRequestBodyBytes.length));
			DataOutputStream wr = new DataOutputStream(conn.getOutputStream());
			wr.write(jsonRequestBodyBytes);
			int responseCode = conn.getResponseCode();
			if (responseCode != 200) {
				fLogger.error("[Vault] ERROR - CredentialResolver: " +
						"Unable to get credential from credential resolver python api.");
				throw new RuntimeException("CredentialResolver: " +
						"Unable to get credential from credential resolver python api.");
			}
			BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			String inputLine;
			StringBuffer response = new StringBuffer();
			while((inputLine = in.readLine()) != null) {
				response.append(inputLine);
			}
			in.close();
			conn.disconnect();
			credential = gson.fromJson(response.toString(), Map.class);
		} catch (IOException ex) {
			throw new RuntimeException(ex);
		}
		try{
			// Parse credential
			switch(credType) {
				// for below listed credential type , just retrieve username and password
				case CRED_TYPE_WINDOWS:
				case CRED_TYPE_SSH_PASSWORD:
				case CRED_TYPE_VMWARE:
				case CRED_TYPE_JDBC:
				case CRED_TYPE_JMS:
				case CRED_TYPE_BASIC_AUTH:
					username = credential.get("username");
					password = credential.get("password");
					if (isNullOrEmpty(password) || isNullOrEmpty(username)) {
						fLogger.error("[Vault] ERROR - CredentialResolver: " +
								"Invalid KV format in vault for credential type: "
								+ credType + ". Is not possible paring credential. " +
								"Please check the KV format in vault. Use the " +
								"following format: {\"username\":\"<username>\", \"password\"=\"<password>\"");
						throw new RuntimeException("CredentialResolver: " +
								"Invalid KV format in vault for credential type: " +
								credType + ". Is not possible paring credential. ");
					}
					break;
				// for below listed credential type , retrieve username, password, ssh_passphrase, ssh_private_key
				case CRED_TYPE_SSH_PRIVATE_KEY:
				case "sn_cfg_ansible":
				case "sn_disco_certmgmt_certificate_ca":
				case "cfg_chef_credentials":
				case "infoblox":
				case "api_key":
					// Read operation
					username = credential.get("username");
					private_key = credential.get("password"); //use corresponding attribute name for private_key
					passphrase = credential.get("ssh_passphrase");
					break;
				case "aws": // access_key, secret_key 	// AWS Support
					username = credential.get("access_key");
					password = credential.get("secret_key");
					break;
				case "ibm": // softlayer_user, softlayer_key, bluemix_key
				case CRED_TYPE_AZURE: // tenant_id, client_id, auth_method, secret_key
				case CRED_TYPE_GCP: // email , secret_key
				default:
					fLogger.error("[Vault] ERROR - CredentialResolver: invalid credential type found.");
					throw new RuntimeException("CredentialResolver: invalid credential type found.");
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		// the resolved credential is returned in a HashMap...
		Map<String, String> result = new HashMap<>();
		/*
		 *     String VAL_USER = "user";
		 *     String VAL_PSWD = "pswd";
		 *     String VAL_PASSPHRASE = "passphrase";
		 *     String VAL_PKEY = "pkey";
		 *     String VAL_AUTHPROTO = "authprotocol";
		 *     String VAL_AUTHKEY = "authkey";
		 *     String VAL_PRIVPROTO = "privprotocol";
		 *     String VAL_PRIVKEY = "privkey";
		 *     String VAL_SECRET_KEY = "secret_key";
		 *     String VAL_CLIENT_ID = "client_id";
		 *     String VAL_TENANT_ID = "tenant_id";
		 *     String VAL_EMAIL = "email";
		 */
		result.put(VAL_USER, username);
		if (isNullOrEmpty(private_key)) {
			result.put(VAL_PSWD, password);
		} else {
			result.put(VAL_PKEY, private_key);
		}
		result.put(VAL_PASSPHRASE, passphrase);
		return result;
	}

	private static boolean isNullOrEmpty(String str) {
		/**
		 * Returns true if the given string is null or empty.
		 */
		return str == null || str.isEmpty();
	}
	private static byte[] getCredentialToApi(){
		/**
		 * Returns the credential from AWS Secrets Manager.
		 */
		String accessKey = System.getenv("AWS_ACCESS_KEY");
		String secretKey = System.getenv("AWS_SECRET_KEY");
		String region = System.getenv("AWS_REGION");
		String accountId = System.getenv("AWS_ACCOUNT_ID");
		String endpoint = System.getenv("AWS_ENDPOINT");
		String secretName = System.getenv("AWS_SECRET_NAME");
		SecretsManagerClient secretsClient = SecretsManagerClient.builder()
				.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
				.endpointOverride(URI.create(endpoint))
				.region(Region.of(region))
				.build();
		GetSecretValueRequest valueRequest = GetSecretValueRequest.builder()
				.secretId(secretName)
				.build();
		GetSecretValueResponse valueResponse = secretsClient.getSecretValue(valueRequest);
		Gson gson = new Gson();
		Map<String,String> credentialApiMap = gson.fromJson(valueResponse.secretString(), Map.class);
		String usernameApi = credentialApiMap.get("username");
		String passwordApi = credentialApiMap.get("password");
		String authApi = usernameApi + ":" + passwordApi;
		return Base64.getEncoder().encode(authApi.getBytes(StandardCharsets.UTF_8));
	}
	/**
	 * Return the API version supported by this class.
	 * Note: should be less than 1.1 for external credential resolver.
	 */
	@Override
	public String getVersion() {
		return "0.0.1";
	}

	//main method to test locally, provide your vault details and test it.
	// TODO: Remove this before moving to production
	public static void main(String[] args) {
		CredentialResolver obj = new CredentialResolver();
		// obj.loadProps();
		// use your local details for testing.
		//obj.hashicorpVaultAddress = "<hashicorp url>";
		//obj.hashicorpVaultToken = "<token>";

		Map<String, String> map = new HashMap<>();
		String credId = "kv/testwin";
		String credType = "windows";
		map.put(ARG_ID, credId);
		map.put(ARG_TYPE, credType);

		Map<String, String> result = obj.resolve(map );
		System.out.println("Result: " + result.toString());
	}
}