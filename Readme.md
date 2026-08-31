# ZetaPay Backend API

**Version:** 1.0.0  
**Framework:** Spring Boot 3.2.3  
**Language:** Java 17

---

## Overview

RESTful API backend for ZentraPay — a **Pan-African payment link generation platform**. Built with Spring Boot, this service handles user authentication, payment link management, payment processing, webhooks, settlement, and seller analytics.

---

## Features

- **JWT Authentication** — Secure user registration and login with email verification
- **Payment Provider Abstraction** — Pluggable provider interface (CashOnRails implementation with HMAC-512 + RSA-SHA256 signing)
- **Pan-African Payouts** — Bank accounts, mobile money (M-Pesa, MTN MoMo), and EFT across NGN, GHS, KES, ZAR, USD
- **Payment Links** — Create shareable links with CSPRNG short codes, single-use, max-uses, expiry
- **Custom Checkout Branding** — Sellers customize logo, colors, and thank-you message per link
- **Idempotent Checkout** — Double-submit protection with configurable reuse window
- **Server-Side Confirmation** — Never trust browser redirects; always verify with provider
- **Automated Settlement** — Platform fee deduction with durable, retryable payout records
- **Payout Reconciliation** — Scheduled retry with exponential backoff for failed payouts
- **Payment Notifications** — Seller alerts and customer receipts via Resend
- **Payment Reminders** — Expiring link and abandoned checkout email nudges
- **Earnings Summary** — Aggregated view across all currencies with per-currency breakdown
- **Seller Analytics** — Daily revenue trends and per-link performance metrics
- **Referral Program** — Unique referral codes with usage tracking
- **API Key Management** — SHA-256 hashed keys with permission scopes for programmatic access
- **Webhook Processing** — Signature-verified inbound events with persistent audit log
- **CORS & Rate Limiting** — Configurable origins and IP-based throttling
- **Swagger/OpenAPI** — Interactive API documentation with JWT authorize button

---

## Technology Stack

- **Java:** 17
- **Spring Boot:** 3.2.3
- **Spring Security:** JWT-based authentication + API key filter
- **Database:** PostgreSQL 17 (Flyway migrations V1–V10)
- **ORM:** Spring Data JPA (Hibernate)
- **API Docs:** SpringDoc OpenAPI 3 (Swagger)
- **Build Tool:** Maven 3.9+
- **Testing:** JUnit 5, Mockito, Testcontainers
- **Email:** Resend (3,000 emails/month free tier)

---

## Project Structure

```
src/main/java/com/zentrapay/
├── config/              # Security, CORS, Swagger configs
├── controller/          # REST API endpoints (8 controllers)
├── service/             # Business logic (12 services + 2 scheduled jobs)
├── repository/          # Database access (9 repositories)
├── entity/              # JPA entities (12 entities)
├── dto/                 # Data transfer objects
│   ├── auth/            # Register, login, auth response
│   ├── checkout/        # Public checkout DTOs
│   ├── payment/         # Payment response, earnings, analytics
│   ├── paymentlink/     # Link CRUD with branding
│   ├── payout/          # Payout account and settlement DTOs
│   ├── referral/        # Referral program DTOs
│   └── apikey/          # API key management DTOs
├── provider/            # Payment provider abstraction + CashOnRails impl
├── security/            # JWT filter, rate limiter, API key filter
├── exception/           # Custom exceptions + global handler
└── util/                # JWT utility
```

---

## API Endpoints

### Public (No Auth)
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/health` | System health check |
| POST | `/api/v1/auth/register` | Register new user |
| POST | `/api/v1/auth/login` | Login user |
| GET | `/api/v1/auth/verify?token=...` | Verify email address |
| GET | `/api/v1/pay/{shortCode}` | View payment link (public) |
| POST | `/api/v1/pay/{shortCode}` | Start payment → get checkout URL |
| GET | `/api/v1/pay/callback?reference=...` | Payment callback (provider redirect) |
| POST | `/api/v1/webhooks/cashonrails` | CashOnRails webhook (signature-verified) |

### Authenticated (JWT Bearer)
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/payment-links` | Create payment link |
| GET | `/api/v1/payment-links` | List your payment links |
| GET | `/api/v1/payment-links/{id}` | Get a payment link |
| DELETE | `/api/v1/payment-links/{id}` | Soft-delete a payment link |
| GET | `/api/v1/payments` | List your payments (filter by status) |
| GET | `/api/v1/payments/{id}` | Get a payment |
| GET | `/api/v1/payments/by-link/{linkId}` | Payments for a specific link |
| GET | `/api/v1/payments/summary` | Earnings summary (aggregated) |
| GET | `/api/v1/payments/analytics` | Seller analytics (daily trends) |
| POST | `/api/v1/payout-accounts/validate` | Validate payout account |
| POST | `/api/v1/payout-accounts` | Save payout account |
| GET | `/api/v1/payout-accounts` | Get your payout account |
| DELETE | `/api/v1/payout-accounts` | Deactivate payout account |
| GET | `/api/v1/payouts` | List your settlements |
| GET | `/api/v1/payouts/{id}` | Get a settlement |
| GET | `/api/v1/referrals/me` | Get your referral code |
| POST | `/api/v1/api-keys` | Create API key |
| GET | `/api/v1/api-keys` | List your API keys |
| DELETE | `/api/v1/api-keys/{id}` | Revoke an API key |

---

## Getting Started

### Prerequisites
- Java 17+
- Maven 3.9+
- PostgreSQL 15+
- Docker (optional, for docker-compose)

### Quick Start
```bash
# 1. Setup database
psql -U postgres -c "CREATE DATABASE zentrapay_dev;"
psql -U postgres -c "CREATE USER zentrapay_user WITH PASSWORD 'zentrapay_dev_password';"
psql -U postgres -c "GRANT ALL ON DATABASE zentrapay_dev TO zentrapay_user;"

# 2. Configure environment
cp .env.example .env
# Edit .env with your values

# 3. Run
mvn spring-boot:run
```

### Docker
```bash
docker-compose up -d
```

**Server starts at:** `http://localhost:8080`  
**Swagger UI:** `http://localhost:8080/swagger-ui/index.html`

---

## Environment Variables

See `.env.example` for all variables. Key ones:

| Variable | Description | Default |
|----------|-------------|---------|
| `DB_URL` | PostgreSQL JDBC URL | — |
| `JWT_SECRET` | HMAC-SHA512 signing key (min 32 bytes) | — |
| `CASHONRAILS_SECRET_KEY` | Payment provider API key | — |
| `CASHONRAILS_ENCRYPTION_KEY` | HMAC key for webhooks/payouts | — |
| `CASHONRAILS_RSA_PRIVATE_KEY` | RSA key for payout signatures | — |
| `RESEND_API_KEY` | Email service API key | — |
| `PLATFORM_FEE_BASIS_POINTS` | Fee in basis points (100 = 1%) | 100 |
| `APP_CORS_ORIGINS` | Comma-separated frontend origins | http://localhost:3000 |

---

## Testing

```bash
# Unit tests
mvn test

# Integration tests (requires Docker for Testcontainers)
mvn verify
```

---

## Security

- BCrypt-12 password hashing
- JWT authentication (HMAC-SHA512, 24h expiry)
- Webhook signature verification (HMAC-512 + optional bearer key)
- Constant-time string comparison (timing attack prevention)
- API keys stored as SHA-256 hashes (never plaintext)
- IP-based rate limiting (15/min on auth, 100/min general)
- CORS configured per environment
- Input validation on all endpoints
- Money stored in minor units (no floating point)
- Server-side payment confirmation (never trust browser redirect)
- Idempotent payment processing (no double charges)

---

## License

Proprietary — Appsware (dev@appsware.ng)
