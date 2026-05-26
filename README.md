# 🎯 E2R - Endpoint To Request (AI-Powered JS Reconnaissance)

<p align="center">
  <img src="https://img.shields.io/badge/Burp_Suite-Extension-orange?style=for-the-badge" alt="Burp Extension">
  <img src="https://img.shields.io/badge/Java-17+-blue?style=for-the-badge" alt="Java 17+">
  <img src="https://img.shields.io/badge/AI-Ollama%20%7C%20Groq%20%7C%20Gemini-green?style=for-the-badge" alt="AI Powered">
  <img src="https://img.shields.io/badge/API-Montoya_API-red?style=for-the-badge" alt="Montoya API">
</p>

---

## 🌟 Introduction

**E2R (Endpoint To Request)** is a state-of-the-art **Burp Suite extension** designed to revolutionize client-side JavaScript reconnaissance. Built on the modern **Montoya API**, E2R goes far beyond legacy scanners. It passively monitors, extracts, and categorizes endpoints, URLs, high-entropy secrets, developer emails, parameters, and DOM-based sources/sinks. 

The crown jewel of E2R is its **AI Workbench**. Using local or cloud-based LLMs, E2R parses the surrounding JavaScript code context of a discovered endpoint and **reconstructs a fully valid, raw HTTP request** (complete with realistic parameters, headers, and request bodies) ready to be tested in Burp Repeater or Intruder.

> [!IMPORTANT]
> **CRITICAL SCOPE REQUIREMENT**:
> For E2R to passively process traffic or run active scans, the target domain **MUST be added to Burp Suite's Target Scope** (`Target` -> `Scope`). E2R strictly ignores all traffic, domains, and files outside of the defined target scope to maintain maximum performance and filter out unrelated third-party noise.

---

## 🔍 How E2R Works (Under the Hood)

E2R functions as an intelligent interceptor and scanner that operates seamlessly during your normal browsing workflow or as an active scanner on-demand.

### The Execution Pipeline:

1. **Traffic Interception & Scope Validation**: The extension intercepts incoming HTTP responses. If a resource is within Burp's **Target Scope**, it proceeds. Otherwise, it is instantly ignored.
2. **MIME & Resource Filtration**: E2R validates if the response is a script (e.g., `.js` file, inside HTML `<script>` tags, JSON responses, or MIME-type scripts). It automatically filters out blacklisted extensions (images, CSS, fonts) and directories (like `/_next/`).
3. **On-the-Fly Beautification**: Minified JavaScript files are difficult for regex matchers and LLMs to read. E2R's internal **Beautifier** expands and formats minified code in-memory. This ensures accurate line number tracking and highly readable code contexts for the LLM.
4. **Pattern & Entropy Extraction**: Multi-threaded scanner engines parse the beautified code using tuned, false-positive-resistant regex patterns to identify:
   * **Endpoints**: API routes and relative paths.
   * **URLs**: Absolute external paths.
   * **Secrets**: High-entropy strings (AWS keys, Stripe credentials, slack webhooks, private keys, API keys).
   * **Emails**: Support or developer emails (filtering out dummy or system emails).
   * **Sensitive Files**: References to configuration, backups, environment, or database files (`.env`, `.conf`, `.sql`, etc.).
   * **Parameters**: Query, JSON, and post parameters found in the JS structure.
   * **DOM Sources & Sinks**: DOM properties vulnerable to Client-Side XSS (e.g., `location.hash`, `innerHTML`, `eval`).
5. **Deduplication and Storage**: Findings are passed to a synchronized, thread-safe **Discovery Store** that deduplicates findings on a unique compound key.
6. **AI Prompt Reconstruction**: When a user selects an endpoint in the **AI Workbench**, the extension isolates the surrounding lines of JavaScript source code (the Context Window). It formats a tailored developer prompt containing:
   * The targeted endpoint path and host.
   * A method hint (inferred from surrounding keywords like `POST`, `fetch`, `axios.put`, etc.).
   * The actual code snippet containing parameters.
7. **LLM Generation**: The prompt is processed via your selected provider. The LLM acts as an expert parser, returning a **RAW HTTP request** with realistic payload values.
8. **Security Testing**: The generated request is loaded directly into Burp Suite, letting you send it straight to **Repeater**, **Intruder**, or execution.

---

## 📊 Flowcharts & Architecture

### System Architecture Flowchart

```mermaid
graph TD
    A[Burp Suite HTTP Traffic / Site Map] -->|Passive Monitor / Context Menu Scan| B[E2R Extension]
    B --> C[E2RHttpHandler / ContextMenuProvider]
    C --> D[JavaScriptAnalyzer]
    D -->|Minified JS?| E[JsBeautifier]
    E --> F[Beautified Source Code]
    D -->|Regular JS?| F
    F --> G[Deduplication & Extraction Engine]
    G --> H[Endpoint / URL Scanning]
    G --> I[Secret / Key Scanning]
    G --> J[Email Scanning]
    G --> K[Sensitive File Scanning]
    G --> L[Parameter & DOM Sources/Sinks Scanning]
    H & I & J & K & L --> M[Unified Discovery Store]
    M --> N[Live Discovery Dashboard]
    N -->|Select Endpoint & Trigger Workbench| O[AI Workbench Panel]
    O -->|Fetch surrounding code context| P[ContextExtractor]
    P --> Q[PromptBuilder]
    Q -->|Formulate Prompt| R[AI Provider Interface]
    R -->|Local Inference| S[Ollama Provider]
    R -->|Cloud Inference| T[Groq / Gemini Providers]
    S & T -->|Response Parser| U[Generated Valid HTTP Request]
    U --> V[Burp Suite Request Editor / Repeater]
```

### AI Request Generation Sequence Diagram

```mermaid
sequenceDiagram
    autonumber
    actor User as Security Researcher
    participant UI as AI Workbench Panel
    participant Ext as Context Extractor
    participant PB as Prompt Builder
    participant Provider as AI Provider (Ollama/Groq/Gemini)
    participant Burp as Burp Suite Editor

    User->>UI: Selects endpoint & Clicks "Generate Request"
    UI->>Ext: Requests source code around matching endpoint
    Ext-->>UI: Returns surrounding JS context (lines & context window)
    UI->>PB: Passes context, endpoint, and detected method
    PB-->>UI: Constructs structured prompt with system instructions
    UI->>Provider: Transmits prompt to selected LLM Model
    Note over Provider: LLM analyzes JS variables,<br/>parameters, headers and body requirements
    Provider-->>UI: Returns raw reconstructed HTTP request
    UI->>UI: Parses response & renders request text area
    User->>Burp: Send Request to Repeater / Intruder or execute
```

---

## ✨ Features

### 1. Unified Live Discovery Dashboard
A clean, tabbed panel that organizes passive scanning findings into categorized tables. It automatically parses JavaScript files to discover and group:

* **Endpoints**: API routes and relative paths extracted from client-side routers or fetch calls.
  ![Endpoints](E2R/media/live_discovery.png)
  
* **URLs**: External absolute URLs showing third-party integrations and backend endpoints.
  ![URLs](E2R/media/url_feature.png)
  
* **Secrets**: API keys, AWS credentials, auth tokens, Slack webhooks, and private keys.
  ![Secrets](E2R/media/secret_feature.png)
  
* **Emails**: Support, developer, and administrator emails.
  
* **Files**: References to sensitive file extensions (e.g., `.sql`, `.conf`, `.env`, `.bak`).
  ![Files](E2R/media/file_feature.png)
  
* **Parameters**: Query and body parameters mapped from JavaScript objects.
  ![Parameters](E2R/media/parameter_feature.png)
  
* **DOM Sources & Sinks**: DOM properties vulnerable to Client-Side XSS, highlighting inputs (Sources) and outputs (Sinks).
  ![DOM Sources](E2R/media/source_feature.png)
  ![DOM Sinks](E2R/media/sink_feature.png)

### 2. Live Context Viewer
Select any discovered item to immediately view the file URL, host, exact matching line, and the **surrounding code block** in a syntax-highlighted console. Know exactly how the endpoint or secret is utilized.

### 3. Fully Configurable Filters (Settings Panel)
Fine-tune E2R's detection criteria directly from the **Settings** tab. Add or remove custom patterns dynamically:
* **Extension Blacklist**: Prevent static media or styling files (like `.png`, `.css`, `.woff2`) from cluttering your results.
* **Path Blacklist**: Discard noise from specific paths (e.g., `/_next/`, `/static/js/`, `/node_modules/`).
![Settings](E2R/media/settings.png)
![Filters](E2R/media/extension_filtering_feature.png)

### 4. Advanced AI Workbench
E2R supports **three major AI providers** with multiple model presets, including custom model inputs and real-time connectivity testers:
1. **Ollama (Local/Offline)**: 100% private, no data leaves your machine. Mapped to speed-optimized coding models like `qwen2.5-coder:7b`.
2. **Groq (Lightning-Fast Cloud)**: Fast, free-tier cloud endpoints using models like `llama-3.3-70b-versatile`.
3. **Google Gemini (Deep Context)**: Perfect for handling extremely large JS context windows using models like `gemini-1.5-flash` or `gemini-2.5-flash`.

![AI Workbench](E2R/media/ai_workbench.png)

---

## 🛠️ Build & Installation

### Prerequisites
* **Java Development Kit (JDK)**: Version 17 or higher.
* **Gradle**: Handled automatically via Gradle Wrapper.
* **Burp Suite Professional or Community**: Edition `2024.12` or newer (essential for Montoya API compatibility).

### Build from Source
Compile and package the extension with a single command:

```bash
# Clone the repository
git clone https://github.com/yourusername/endpoint2Request.git
cd endpoint2Request/E2R

# Grant execution permissions to Gradle wrapper (Linux/macOS)
chmod +x gradlew

# Build the JAR file
./gradlew build
```

The compiled extension will be output directly to the release directory:
* 📂 `release/E2R-1.2.0.jar`

### Loading into Burp Suite
1. Launch **Burp Suite**.
2. Navigate to the **Extensions** tab -> **Installed** sub-tab.
3. Click the **Add** button.
4. Set **Extension type** to `Java`.
5. Click **Select file** and browse to `release/E2R-1.2.0.jar`.
6. Click **Next**. The extension is now successfully installed, and a new tab named **E2R - Endpoint To Request** will appear in the main suite bar!

---

## ⚙️ AI Configuration

Set up your preferred AI provider in the **Settings** tab:

### 1. Local Processing (Ollama)
* **Setup**: Install [Ollama](https://ollama.com) on your computer.
* **Pull Model**: Run `ollama pull qwen2.5-coder:7b` (recommended) or `ollama pull deepseek-coder`.
* **Configuration**: Select **Ollama (Local)** in the provider dropdown. Ensure the URL is `http://localhost:11434`.
* **Testing**: Click **Test Connection** to verify that Ollama is running and the model is loaded.

### 2. Groq Cloud (Free)
* **Setup**: Log in to [Groq Console](https://console.groq.com) and generate a free API Key.
* **Configuration**: Select **Groq (Cloud)**, paste your API Key in the field, and choose a model (e.g., `llama-3.3-70b-versatile`).
* **Saving**: Save settings, then click **Test Connection**.

### 3. Google Gemini
* **Setup**: Obtain an API Key from the Google AI Studio.
* **Configuration**: Select **Gemini (Cloud)**, input your API key, and choose `gemini-1.5-flash`.

---

## ⚠️ Troubleshooting Ollama 404 Error

If you click **Test Connection** in the Settings tab and see `✓ Connected to Ollama (Local)`, but when generating a request in the Workbench you receive:
```text
Error generating request:

Ollama error: 404 Not Found
```
Please verify the following three items to resolve the issue:

1. **Verify Pulled Model Name**: Ollama returns `404 Not Found` if the model specified in E2R Settings does not exist or has not been pulled.
2. **Check Model Spelling**: Run `ollama list` in your terminal to see the exact downloaded names. Ensure the model name in E2R Settings matches the `NAME` column of `ollama list` exactly (e.g. `qwen2.5-coder:7b` instead of just `qwen2.5-coder`).
3. **Pull the Model**: If the model is missing, run `ollama pull <model-name>` (e.g. `ollama pull qwen2.5-coder:7b`) in your terminal.

---

## 🚀 Step-by-Step Security Research Workflow

Maximize your reconnaissance efficiency using this recommended workflow:

### Step 1: Passive Observation
1. **Critical Scope Setup**: Add your target domain to Burp's **Target Scope** (`Target` -> `Scope`).
2. **Proxy Traffic**: Turn on your browser proxy and navigate through the target application.
3. **Passive Scans**: As you click around, E2R automatically intercept and scans any incoming `.js` file or embedded script in real-time.
4. **View Findings**: Go to **E2R** -> **Live Discovery** to watch findings populate.

### Step 2: Target Scope Re-Scanning
1. **Site Map Scan**: Go to Burp's **Target** -> **Site Map**.
2. **Right-Click**: Right-click on the target domain folder (ensure it is in Scope!).
3. **Trigger Scan**: Select **Extensions** -> **E2R: Scan for Endpoints, Secrets & Files**.
4. **Analysis**: E2R will actively query and re-scan every cached JavaScript file in the selected directory.

### Step 3: AI Request Generation
1. Go to the **E2R** tab -> **AI Workbench**.
2. From the list of discovered endpoints, select a target path (e.g. `/api/v1/user/update`).
3. Click the **Generate Request** button.
4. The AI parses the surrounding JavaScript parameters and returns a beautifully structured, raw HTTP request in the text box.

### Step 4: Vulnerability Scanning & Testing
1. Review the generated request in the editor. You can modify parameters, headers, or body fields directly inside E2R.
2. Right-click inside the request editor box.
3. Send the request directly to **Repeater** to execute, or **Intruder** to fuzz!

---

## 🙏 Credits & Inspiration

E2R is built on the shoulders of giants. Special credits go to the authors of:
* **[JSAnalyzer](https://github.com/jenish-sojitra/JSAnalyzer)**: For the baseline regex-based JS parsing structures.
* **LinkFinder**: For pioneer research in JavaScript endpoint discovery.
* **Burp Suite Montoya API**: For providing the ultimate developer experience for Burp extension developers.

---

## 📄 License
This project is licensed under the **MIT License**. Feel free to use, modify, and distribute it in your own commercial or open-source security operations.
