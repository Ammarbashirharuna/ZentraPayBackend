package com.zentrapay.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;

/**
 * Paystack API Client
 *
 * Handles all communication with Paystack:
 * - Verify bank accounts
 * - Create subaccounts
 * - Handle responses and errors
 *
 * Why a separate client class?
 * - Centralizes all Paystack API calls
 * - Easy to mock in tests
 * - Reusable across services
 * - One place to add logging, error handling, retries
 */
@Component
@Slf4j
public class PaystackClient {

    /**
     * Our Paystack secret key from .env
     * Used to authenticate all API calls
     * Example: sk_test_xyz123...
     */
    @Value("${paystack.secret-key}")
    private String secretKey;

    /**
     * Paystack API base URL
     * Test: https://api.paystack.co
     * (same for both test and live, just different keys)
     */
    @Value("${paystack.base-url}")
    private String baseUrl;

    /**
     * HttpClient for making API calls
     * Spring manages this singleton
     */
    private final HttpClient httpClient = HttpClient.newHttpClient();

    /**
     * JSON parser
     * Paystack returns JSON, we parse it to get data
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Verify bank account with Paystack
     *
     * When seller enters their account number:
     * 1. We call Paystack to verify it exists
     * 2. Paystack returns the account holder's name
     * 3. We show the name to seller for confirmation
     *
     * Paystack endpoint: POST /bank/resolve
     * Request:
     * {
     *   "account_number": "0123456789",
     *   "bank_code": "058"
     * }
     *
     * Response (if account exists):
     * {
     *   "status": true,
     *   "message": "Account number resolved",
     *   "data": {
     *     "account_number": "0123456789",
     *     "account_name": "Ammar Bashir Haruna",
     *     "bank_id": 9
     *   }
     * }
     *
     * Response (if account doesn't exist):
     * {
     *   "status": false,
     *   "message": "Could not resolve account number"
     * }
     *
     * @param bankCode Bank code (e.g., "058")
     * @param accountNumber Account number (e.g., "0123456789")
     * @return Account name if valid, null if invalid
     * @throws Exception if API call fails
     */
    public String verifyBankAccount(String bankCode, String accountNumber) {
        log.info("Verifying bank account: {}/{}", bankCode, accountNumber);

        try {
            // Build the request URL
            String url = baseUrl + "/bank/resolve?account_number="
                    + accountNumber + "&bank_code=" + bankCode;

            // Create HTTP request with Paystack auth header
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .header("Authorization", "Bearer " + secretKey)
                    .header("Content-Type", "application/json")
                    .build();

            // Send request and get response
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            // Parse JSON response
            JsonNode jsonResponse = objectMapper.readTree(response.body());

            // Check if Paystack says the account is valid
            if (jsonResponse.get("status").asBoolean()) {
                // Account is valid, extract the name
                String accountName = jsonResponse
                        .get("data")
                        .get("account_name")
                        .asText();

                log.info("Account verified successfully: {}", accountName);
                return accountName;
            } else {
                // Account is invalid
                String message = jsonResponse.get("message").asText();
                log.warn("Account verification failed: {}", message);
                return null;
            }

        } catch (Exception e) {
            log.error("Error verifying bank account: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to verify bank account: "
                    + e.getMessage());
        }
    }

    /**
     * Create Paystack subaccount
     *
     * After seller confirms their account, we create a subaccount.
     * This subaccount is linked to their bank account.
     * When customers pay → Paystack automatically sends money to this account.
     *
     * Paystack endpoint: POST /subaccount
     * Request:
     * {
     *   "business_name": "Ammar's Web Design",
     *   "settlement_bank": "058",           // bank code
     *   "account_number": "0123456789",
     *   "percentage_charge": 0              // we don't charge extra (Paystack handles it)
     * }
     *
     * Response:
     * {
     *   "status": true,
     *   "message": "Subaccount created",
     *   "data": {
     *     "subaccount_code": "ACCT_1a2b3c4d",
     *     "business_name": "Ammar's Web Design",
     *     "settlement_bank": "058",
     *     "account_number": "0123456789",
     *     ...
     *   }
     * }
     *
     * @param businessName Seller's business name
     * @param bankCode Bank code (e.g., "058")
     * @param accountNumber Account number (e.g., "0123456789")
     * @return Subaccount code (e.g., "ACCT_xyz123") if success, null if failed
     * @throws Exception if API call fails
     */
    public String createSubaccount(String businessName,
                                   String bankCode,
                                   String accountNumber) {
        log.info("Creating Paystack subaccount for: {}", businessName);

        try {
            // Build the request body
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("business_name", businessName);
            requestBody.put("settlement_bank", bankCode);
            requestBody.put("account_number", accountNumber);
            requestBody.put("percentage_charge", 0);
            // percentage_charge = 0 because we don't charge extra
            // Paystack's standard fee is already in the 1.5% + ₦100

            // Convert to JSON
            String jsonBody = objectMapper.writeValueAsString(requestBody);

            // Create HTTP request
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/subaccount"))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .header("Authorization", "Bearer " + secretKey)
                    .header("Content-Type", "application/json")
                    .build();

            // Send request
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            // Parse response
            JsonNode jsonResponse = objectMapper.readTree(response.body());

            // Check if subaccount was created
            if (jsonResponse.get("status").asBoolean()) {
                // Subaccount created, extract the code
                String subaccountCode = jsonResponse
                        .get("data")
                        .get("subaccount_code")
                        .asText();

                log.info("Subaccount created successfully: {}", subaccountCode);
                return subaccountCode;
            } else {
                // Subaccount creation failed
                String message = jsonResponse.get("message").asText();
                log.warn("Subaccount creation failed: {}", message);
                return null;
            }

        } catch (Exception e) {
            log.error("Error creating Paystack subaccount: {}",
                    e.getMessage(), e);
            throw new RuntimeException("Failed to create subaccount: "
                    + e.getMessage());
        }
    }
}