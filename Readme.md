# ZentraPay Backend API

**Version:** 1.0.0  
**Framework:** Spring Boot 3.2.3  
**Language:** Java 17

---

## 🎯 **Overview**


RESTful API backend for ZentraPay - a payment link generation platform. Built with Spring Boot, this service handles user authentication, payment link management, payment processing, and webhook handling.

---

## ✨ **Features**

- 🔐 **JWT Authentication** - Secure user registration and login
- 💳 **Payment Provider Integration** - Paystack & Stripe support
- 🔗 **Payment Link Management** - Create, update, and track payment links
- 📧 **Email Notifications** - Automated payment confirmations
- 🔄 **Webhook Processing** - Real-time payment status updates
- 📊 **Analytics** - Revenue tracking and payment insights
- 📝 **API Documentation** - Interactive Swagger UI

---

## 🛠️ **Technology Stack**

- **Java:** 17
- **Spring Boot:** 3.2.3
- **Spring Security:** JWT-based authentication
- **Database:** PostgreSQL 15
- **ORM:** Spring Data JPA (Hibernate)
- **API Docs:** SpringDoc OpenAPI 3 (Swagger)
- **Build Tool:** Maven 3.9+
- **Testing:** JUnit 5, Spring Boot Test

---

## 📁 **Project Structure**
```
backend/
├── src/main/
│   ├── java/com/zentrapay/
│   │   ├── config/              # Configuration classes
│   │   ├── controller/          # REST API endpoints
│   │   ├── service/             # Business logic
│   │   ├── repository/          # Database access
│   │   ├── entity/              # JPA entities
│   │   ├── dto/                 # Data transfer objects
│   │   ├── exception/           # Custom exceptions
│   │   └── util/                # Utility classes
│   └── resources/
│       └── application.properties
├── pom.xml
├── .env.example
└── README.md
```

---

## 🚀 **Getting Started**

### **Prerequisites**

- Java 17 or higher
- Maven 3.9+
- PostgreSQL 15+
- Git

### **Installation**

#### **1. Clone the Repository**
```bash
git clone https://github.com/YOUR_USERNAME/zentrapay-backend.git
cd zentrapay-backend
```

#### **2. Setup Database**
```bash
# Login to PostgreSQL
psql -U postgres

# Run these commands
CREATE DATABASE zentrapay_dev;
CREATE USER zentrapay_user WITH ENCRYPTED PASSWORD 'zentrapay_dev_password';
GRANT ALL PRIVILEGES ON DATABASE zentrapay_dev TO zentrapay_user;

# Connect to database
\c zentrapay_dev

# Grant permissions
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO zentrapay_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO zentrapay_user;

\q
```

#### **3. Configure Environment Variables**
```bash
# Copy example file
cp .env.example .env

# Edit .env with your values
```

**`.env` file:**
```bash
JWT_SECRET=your-secure-64-character-secret-here-generate-using-openssl
JWT_EXPIRATION=86400000
```

**Generate secure secret:**
```bash
openssl rand -base64 64
```

#### **4. Run the Application**
```bash
# Install dependencies and build
mvn clean install

# Run application
mvn spring-boot:run
```

**Server starts at:** `http://localhost:8080`

---

## 📡 **API Endpoints**

### **Base URL:** `http://localhost:8080/api/v1`

### **Authentication**

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/auth/register` | Register new user |
| POST | `/auth/login` | Login user |

### **Health Check**

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/health` | Service health status |

**Full API Documentation:** http://localhost:8080/swagger-ui/index.html

---

## 🧪 **Testing**

### **Run Tests**
```bash
mvn test
```

### **Test with Postman**

**Register User:**
```
POST http://localhost:8080/api/v1/auth/register
Content-Type: application/json

{
  "email": "test@zentrapay.com",
  "password": "TestP@ss123",
  "fullName": "Test User"
}
```

**Login User:**
```
POST http://localhost:8080/api/v1/auth/login
Content-Type: application/json

{
  "email": "test@zentrapay.com",
  "password": "TestP@ss123"
}
```

---

## 🔒 **Security**

- ✅ BCrypt password hashing (cost factor 12)
- ✅ JWT authentication (HS512 algorithm)
- ✅ Input validation on all endpoints
- ✅ SQL injection prevention (JPA)
- ✅ Environment variables for secrets
- ✅ CORS configuration
- ✅ Rate limiting ready

---

## 📊 **Database Schema**

### **Tables:**

- `users` - User accounts
- `payment_providers` - Connected Paystack/Stripe accounts
- `payment_links` - Generated payment links
- `payments` - Transaction records
- `webhooks` - Webhook event logs

---

## 🚢 **Deployment**

### **Production Deployment (Railway)**

1. Push code to GitHub
2. Connect Railway to repository
3. Set environment variables:
   - `JWT_SECRET`
   - `JWT_EXPIRATION`
   - `SPRING_DATASOURCE_URL`
   - `SPRING_DATASOURCE_USERNAME`
   - `SPRING_DATASOURCE_PASSWORD`
4. Deploy automatically



## 🤝 **Contributing**

1. Fork the repository
2. Create feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit changes (`git commit -m 'Add AmazingFeature'`)
4. Push to branch (`git push origin feature/AmazingFeature`)
5. Open Pull Request

---

## 📝 **Development Guidelines**

- Follow Spring Boot best practices
- Write unit tests for new features
- Update Swagger documentation
- Use meaningful commit messages
- Keep code DRY (Don't Repeat Yourself)




