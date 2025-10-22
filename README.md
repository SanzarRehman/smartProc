# Procurement Email Automation System

A POC Spring Boot application that automates procurement workflows by monitoring emails, using AI for classification and analysis, and orchestrating purchase order generation with accounting updates.

## Overview

This system demonstrates an automated procurement workflow that:
1. Monitors an email inbox for procurement requests
2. Uses Gemini AI to classify and extract request details
3. Retrieves user context from Keycloak
4. Checks inventory availability (mock data for POC)
5. Recommends items based on user role and preferences
6. Generates purchase orders for confirmed requests
7. Records accounting transactions (simulated GL entries)
8. Sends automated email responses at each step

## Prerequisites

- **Java 17 or higher** - Required for Spring Boot 3.x
- **Maven 3.8+** - Build tool
- **PostgreSQL 12+** - Database server
- **Email account with IMAP access** - Gmail, Outlook, or any IMAP-enabled email
- **Keycloak server** (optional for POC) - Can run locally via Docker
- **Gemini API key** - Get from Google AI Studio

## Quick Start

### 1. Clone and Build

```bash
git clone <repository-url>
cd procurement-email-automation
mvn clean package
```

### 2. Setup PostgreSQL Database

**Option A: Using existing PostgreSQL**
```bash
# Connect to PostgreSQL
psql -U postgres

# Create database and user
CREATE DATABASE mydb;
CREATE USER admin WITH PASSWORD 'admin123';
GRANT ALL PRIVILEGES ON DATABASE mydb TO admin;
\q
```

**Option B: Using Docker**
```bash
docker run --name procurement-postgres \
  -e POSTGRES_DB=mydb \
  -e POSTGRES_USER=admin \
  -e POSTGRES_PASSWORD=admin123 \
  -p 5432:5432 \
  -d postgres:15
```

### 3. Set Environment Variables

Create a `.env` file or export these variables:

```bash
# Database Configuration
export DATABASE_URL=jdbc:postgresql://localhost:5432/mydb
export DATABASE_USERNAME=admin
export DATABASE_PASSWORD=admin123
export DDL_AUTO=update  # Use 'update' to preserve data, 'create-drop' to reset on restart

# Email Configuration (Gmail example)
export EMAIL_HOST=imap.gmail.com
export EMAIL_PORT=993
export EMAIL_USERNAME=your-email@gmail.com
export EMAIL_PASSWORD=your-app-password
export EMAIL_PROTOCOL=imaps

# Keycloak Configuration (optional for POC)
export KEYCLOAK_URL=http://localhost:8080
export KEYCLOAK_REALM=procurement
export KEYCLOAK_CLIENT_SECRET=your-client-secret

# Gemini API Configuration
export GEMINI_API_URL=https://generativelanguage.googleapis.com/v1beta
export GEMINI_API_KEY=your-gemini-api-key
```

### 3. Run the Application

```bash
java -jar target/procurement-email-automation-1.0.0-SNAPSHOT.jar
```

Or use Maven:
```bash
mvn spring-boot:run
```

## Required Environment Variables

### Email Configuration

| Variable | Description | Example | Required |
|----------|-------------|---------|----------|
| `EMAIL_HOST` | IMAP server hostname | `imap.gmail.com` | Yes |
| `EMAIL_PORT` | IMAP server port | `993` | No (default: 993) |
| `EMAIL_USERNAME` | Email account username | `procurement@company.com` | Yes |
| `EMAIL_PASSWORD` | Email account password or app password | `your-app-password` | Yes |
| `EMAIL_PROTOCOL` | Email protocol | `imaps` | No (default: imaps) |

**Gmail Setup:**
1. Enable IMAP in Gmail settings
2. Generate an App Password (if 2FA is enabled)
3. Use the App Password as `EMAIL_PASSWORD`

### Keycloak Configuration

| Variable | Description | Example | Required |
|----------|-------------|---------|----------|
| `KEYCLOAK_URL` | Keycloak server URL | `http://localhost:8080` | No (POC can run without) |
| `KEYCLOAK_REALM` | Keycloak realm name | `procurement` | No (default: procurement) |
| `KEYCLOAK_CLIENT_SECRET` | Client secret for procurement-service | `abc123...` | No (POC can run without) |

**Keycloak Setup (Optional):**
```bash
# Run Keycloak with Docker
docker run -p 8080:8080 \
  -e KEYCLOAK_ADMIN=admin \
  -e KEYCLOAK_ADMIN_PASSWORD=admin \
  quay.io/keycloak/keycloak:latest start-dev
```

### Gemini API Configuration

| Variable | Description | Example | Required |
|----------|-------------|---------|----------|
| `GEMINI_API_URL` | Gemini API base URL | `https://generativelanguage.googleapis.com/v1beta` | No (has default) |
| `GEMINI_API_KEY` | Your Gemini API key | `AIza...` | Yes |

**Get Gemini API Key:**
1. Visit [Google AI Studio](https://makersuite.google.com/app/apikey)
2. Create a new API key
3. Copy and use as `GEMINI_API_KEY`

## Sample Configuration Values

For testing purposes, you can use these sample values:

```yaml
# application-dev.yml (create this file for local development)
spring:
  mail:
    host: imap.gmail.com
    port: 993
    username: test-procurement@gmail.com
    password: your-app-password
    protocol: imaps

keycloak:
  auth-server-url: http://localhost:8080
  realm: procurement
  resource: procurement-service
  credentials:
    secret: test-secret-123

gemini:
  api:
    url: https://generativelanguage.googleapis.com/v1beta
    key: your-api-key-here
    timeout: 10000
    max-retries: 3

email:
  polling:
    interval: 60000  # Poll every 60 seconds
    max-messages-per-poll: 10
```

## Build and Run Instructions

### Build the Project

```bash
# Clean and build
mvn clean package

# Skip tests (faster build)
mvn clean package -DskipTests

# Build with specific profile
mvn clean package -Pdev
```

### Run the Application

**Option 1: Using JAR file**
```bash
java -jar target/procurement-email-automation-1.0.0-SNAPSHOT.jar
```

**Option 2: Using Maven**
```bash
mvn spring-boot:run
```

**Option 3: With environment variables inline**
```bash
EMAIL_HOST=imap.gmail.com \
EMAIL_USERNAME=test@gmail.com \
EMAIL_PASSWORD=app-password \
GEMINI_API_KEY=your-key \
java -jar target/procurement-email-automation-1.0.0-SNAPSHOT.jar
```

**Option 4: With Spring profiles**
```bash
java -jar target/procurement-email-automation-1.0.0-SNAPSHOT.jar --spring.profiles.active=dev
```

### Verify Startup

After starting, you should see:
```
Starting Procurement Email Automation System...
================================================================================
Procurement Email Automation System - Configuration Verification
================================================================================
Email Host: imap.gmail.com
Email Port: 993
Email Protocol: imaps
Keycloak Server: http://localhost:8080
Keycloak Realm: procurement
Gemini API URL: https://generativelanguage.googleapis.com/v1beta
Email Polling Interval: 60000 ms
H2 Console Enabled: true
H2 Console Path: /h2-console
================================================================================
System is ready to process procurement emails
================================================================================
```

## Configuration

The application uses `application.yml` for configuration. You can override properties using:

1. **Environment variables** (recommended for secrets)
2. **Command line arguments**: `--spring.mail.host=smtp.example.com`
3. **Profile-specific files**: `application-{profile}.yml`
4. **External config file**: `--spring.config.location=/path/to/config/`

### Configuration Precedence (highest to lowest)
1. Command line arguments
2. Environment variables
3. Profile-specific application files
4. Default application.yml

## H2 Database Console

Access the H2 console for debugging and data inspection:

**URL:** http://localhost:8080/h2-console

**Connection Settings:**
- JDBC URL: `jdbc:h2:mem:procurementdb`
- Username: `sa`
- Password: (leave empty)
- Driver Class: `org.h2.Driver`

**Available Tables:**
- `PURCHASE_ORDER` - Generated purchase orders
- `GENERAL_LEDGER_ENTRY` - Accounting transactions
- `EMAIL_PROCESSING_STATE` - Email workflow states

## API Endpoints for Testing

### H2 Database Console
- **URL:** `http://localhost:8080/h2-console`
- **Purpose:** View database tables and data

### Spring Boot Actuator (if enabled)
- **Health Check:** `http://localhost:8080/actuator/health`
- **Info:** `http://localhost:8080/actuator/info`

### Testing the Workflow

**1. Send a test procurement email to the configured inbox:**

```
Subject: Need a new laptop
Body: Hi, I need a laptop for development work. Preferably something with good performance.
```

**2. Monitor the logs:**
```bash
tail -f logs/application.log
```

**3. Check the H2 console for:**
- Email processing state in `EMAIL_PROCESSING_STATE` table
- Generated recommendations (check logs)

**4. Reply to the recommendation email with:**
```
Subject: Re: Procurement Request - Recommendations
Body: I confirm the Dell XPS 15
```

**5. Verify in H2 console:**
- Purchase order in `PURCHASE_ORDER` table
- GL entries in `GENERAL_LEDGER_ENTRY` table

## Project Structure

```
procurement-email-automation/
├── src/main/java/com/procurement/email/
│   ├── ProcurementEmailApplication.java    # Main application class
│   ├── config/                              # Configuration classes
│   │   ├── CacheConfig.java                 # Caching configuration
│   │   ├── EmailPollerConfig.java           # Email polling setup
│   │   ├── GeminiConfig.java                # Gemini API config
│   │   ├── IntegrationFlowConfig.java       # Spring Integration flows
│   │   ├── KeycloakConfig.java              # Keycloak setup
│   │   └── MessageChannelConfig.java        # Message channels
│   ├── integration/dto/                     # Gemini API DTOs
│   │   ├── GeminiClassificationRequest.java
│   │   ├── GeminiClassificationResponse.java
│   │   ├── GeminiExtractionRequest.java
│   │   ├── GeminiExtractionResponse.java
│   │   ├── GeminiRecommendationRequest.java
│   │   └── GeminiRecommendationResponse.java
│   ├── model/                               # Domain models and entities
│   │   ├── EmailMessage.java
│   │   ├── EmailProcessingState.java
│   │   ├── GeneralLedgerEntry.java
│   │   ├── Item.java
│   │   ├── ProcurementRequest.java
│   │   ├── PurchaseOrder.java
│   │   └── UserContext.java
│   ├── repository/                          # JPA repositories
│   │   ├── EmailProcessingStateRepository.java
│   │   ├── GeneralLedgerEntryRepository.java
│   │   └── PurchaseOrderRepository.java
│   └── service/                             # Business logic services
│       ├── AccountingAgentService.java      # GL entry management
│       ├── EmailProcessingService.java      # Workflow orchestration
│       ├── EmailSenderService.java          # Email sending
│       ├── GeminiAIService.java             # AI classification/extraction
│       ├── InventoryService.java            # Mock inventory
│       ├── KeycloakIntegrationService.java  # User context retrieval
│       └── ProcurementAgentService.java     # PO generation
├── src/main/resources/
│   └── application.yml                      # Application configuration
├── pom.xml                                  # Maven dependencies
└── README.md                                # This file
```

## Features

- **Automated Email Polling:** Monitors inbox every 60 seconds
- **AI-Powered Classification:** Uses Gemini API to identify procurement requests
- **Context-Aware Recommendations:** Considers user role, designation, and preferences
- **Inventory Management:** Checks available items (mock data for POC)
- **Purchase Order Generation:** Creates POs with unique numbers
- **Accounting Integration:** Records GL entries for allocations and purchases
- **Automated Responses:** Sends recommendation and confirmation emails
- **Error Handling:** Retry logic and graceful degradation
- **State Tracking:** Maintains workflow state in database

## Testing

### Run All Tests
```bash
mvn test
```

### Run Specific Test Class
```bash
mvn test -Dtest=EmailProcessingServiceTest
```

### Run with Coverage
```bash
mvn clean test jacoco:report
```

### Integration Testing
```bash
mvn verify
```

## Troubleshooting

### Email Connection Issues

**Problem:** Cannot connect to email server

**Solutions:**
- Verify `EMAIL_HOST` and `EMAIL_PORT` are correct
- For Gmail, enable "Less secure app access" or use App Password
- Check firewall settings
- Verify IMAP is enabled in email account settings

### Gemini API Errors

**Problem:** AI classification fails

**Solutions:**
- Verify `GEMINI_API_KEY` is valid
- Check API quota limits
- Review Gemini API status page
- Check logs for detailed error messages

### Keycloak Connection Issues

**Problem:** User context retrieval fails

**Solutions:**
- Verify Keycloak server is running
- Check `KEYCLOAK_URL` and `KEYCLOAK_REALM`
- Verify client secret is correct
- System will proceed without user context if Keycloak is unavailable

### Database Issues

**Problem:** Cannot access H2 console

**Solutions:**
- Verify application is running
- Check URL: `http://localhost:8080/h2-console`
- Ensure `spring.h2.console.enabled=true` in config
- Use JDBC URL: `jdbc:h2:mem:procurementdb`

## Docker Support (Optional)

### Build Docker Image
```bash
docker build -t procurement-email-automation .
```

### Run with Docker
```bash
docker run -p 8080:8080 \
  -e EMAIL_HOST=imap.gmail.com \
  -e EMAIL_USERNAME=your-email@gmail.com \
  -e EMAIL_PASSWORD=your-app-password \
  -e GEMINI_API_KEY=your-api-key \
  procurement-email-automation
```

### Docker Compose (with Keycloak)
```yaml
version: '3.8'
services:
  keycloak:
    image: quay.io/keycloak/keycloak:latest
    environment:
      KEYCLOAK_ADMIN: admin
      KEYCLOAK_ADMIN_PASSWORD: admin
    ports:
      - "8080:8080"
    command: start-dev

  procurement-app:
    image: procurement-email-automation
    depends_on:
      - keycloak
    environment:
      EMAIL_HOST: imap.gmail.com
      EMAIL_USERNAME: your-email
      EMAIL_PASSWORD: your-password
      GEMINI_API_KEY: your-key
      KEYCLOAK_URL: http://keycloak:8080
    ports:
      - "8081:8080"
```

## Development Notes

### POC Limitations

This is a POC implementation with:
- **Mock inventory data** - Hardcoded items (laptops, monitors, accessories)
- **Simulated accounting** - GL entries logged but not sent to real system
- **H2 in-memory database** - Data lost on restart
- **Basic error handling** - Simplified for demonstration
- **No authentication** - H2 console and endpoints are open

### Production Considerations

For production deployment, consider:
1. **Real Inventory Integration** - Connect to actual inventory management system
2. **Real Procurement System** - Integrate with ERP or procurement platform
3. **Persistent Database** - Use PostgreSQL, MySQL, or Oracle
4. **Security** - Add authentication, authorization, and encryption
5. **Monitoring** - Add APM, metrics, and alerting
6. **Scalability** - Use message queues for high volume
7. **Audit Trail** - Comprehensive logging and audit records
8. **Approval Workflows** - Multi-level approval processes
9. **Vendor Management** - Integration with vendor systems
10. **Budget Controls** - Budget checking and approval limits

## License

This is a POC project for demonstration purposes.

## Support

For issues or questions:
1. Check the troubleshooting section above
2. Review application logs
3. Inspect H2 database for data issues
4. Verify all environment variables are set correctly
