# WebSocket Logger++ Query Syntax & Filtering Guide

This document is the comprehensive reference guide for creating filters and queries in **WebSocket Logger++**.

---

## Table of Contents
1. [Syntax Basics](#1-syntax-basics)
2. [Fields Reference](#2-fields-reference)
3. [Comparison Operators](#3-comparison-operators)
4. [Logical Operators & Precedence](#4-logical-operators--precedence)
5. [Direction Aliases](#5-direction-aliases)
6. [Real-World Pentesting & Debugging Recipes](#6-real-world-pentesting--debugging-recipes)
7. [Tips & Best Practices](#7-tips--best-practices)

---

## 1. Syntax Basics

### Free-Text Search
If you enter an unquoted single word or a quoted phrase without specifying a field, the engine performs a full-text search across:
- `payload`
- `host`
- `path`
- `url`
- `comment`

**Examples:**
```text
admin
"unauthorized user"
token
```

### Field-Based Expressions
Structured queries follow the format:
```text
<field> <operator> <value>
```

**Examples:**
```text
payload contains "Bearer"
length > 128
dir == client
```

### Case Insensitivity
All field names, operators, and string matching comparisons are case-insensitive:
```text
payload contains "admin"
PAYLOAD CONTAINS "ADMIN"
```
*(Both evaluate identically).*

### Quotes & Escaping
- String values containing spaces, special characters, or JSON formatting must be enclosed in double quotes (`"..."`) or single quotes (`'...'`).
- Use backslashes to escape inner quotes:
  ```text
  payload contains "{\"status\":\"ok\"}"
  ```
- Or use single quotes to avoid escaping double quotes:
  ```text
  payload contains '{"status":"ok"}'
  ```

---

## 2. Fields Reference

| Field Name | Aliases | Data Type | Description |
|---|---|---|---|
| `payload` | `body`, `data`, `p` | String | The text payload of the WebSocket frame |
| `dir` | `direction`, `d` | Direction | Transmission direction (`client`, `server`, etc.) |
| `host` | `h` | String | Target host or IP address |
| `path` | - | String | WebSocket endpoint URI path (e.g., `/socket.io/`, `/ws`) |
| `url` | - | String | Full WebSocket URL |
| `length` | `len`, `size` | Integer | Frame payload length in bytes |
| `type` | - | String | Frame payload type (`Text` or `Binary`) |
| `tool` | - | String | Burp source tool (`Proxy`, `Repeater`, `Extensions`) |
| `id` | - | Integer | Sequential frame ID in the logger |
| `port` | - | Integer | Destination TCP port (e.g., `443`, `80`) |
| `conn` | `connection` | Integer | WebSocket connection ID |
| `comment` | - | String | User-added comment on the log entry |

---

## 3. Comparison Operators

### String Operators
| Operator | Description | Example |
|---|---|---|
| `==` or `=` | Exact match | `path == "/ws/v1/chat"` |
| `!=` | Exact mismatch | `type != "Binary"` |
| `contains` | Substring match | `payload contains "Bearer"` |
| `!contains` | Substring does not match | `payload !contains "heartbeat"` |
| `startswith` | Prefix match | `path startswith "/api/v2"` |
| `endswith` | Suffix match | `path endswith ".json"` |
| `matches` or `regex` | Regular expression match | `payload matches "user_id=\d+"` |
| `!matches` or `!regex`| Regular expression mismatch | `payload !matches "^\{.*\}$"` |

### Numeric Operators
Applicable to `length`, `id`, `port`, `conn`:
- `>` (greater than)
- `<` (less than)
- `>=` (greater than or equal)
- `<=` (less than or equal)
- `==` or `=` (equal)
- `!=` (not equal)

**Examples:**
```text
len > 512
length <= 64
port == 8443
id >= 100
```

---

## 4. Logical Operators & Precedence

Combine multiple expressions using:
- **`AND`** or **`&&`**: Both expressions must evaluate to true.
- **`OR`** or **`||`**: At least one expression must evaluate to true.
- **`NOT`** or **`!`**: Negates the following expression.
- **Parentheses `( ... )`**: Enforce grouping and precedence.

**Examples:**
```text
dir == client and length > 100
dir == server or payload contains "error"
not (payload contains "ping")
(dir == client and len > 200) or (dir == server and payload contains "unauthorized")
```

---

## 5. Direction Aliases

The `dir` field supports multiple intuitive aliases:

### Outgoing (Client to Server)
- `client`
- `outgoing`
- `out`
- `c2s`
- `"to server"`
- `to_server`
- `toserver`

### Incoming (Server to Client)
- `server`
- `incoming`
- `in`
- `s2c`
- `"to client"`
- `to_client`
- `toclient`

---

## 6. Real-World Pentesting & Debugging Recipes

### 1. Filtering Out Repetitive Keepalives & Heartbeats
Often, clients send empty objects (`{}`) or pings to keep the connection alive, while the server echoes responses with varying timestamps:

- **Filter Out Client `{}` and Server Heartbeats:**
  ```sql
  not (dir == client and payload == "{}") and not (dir == server and payload contains "arnstep")
  ```

- **Filter Using Regex for Dynamic Timestamps:**
  ```sql
  not (dir == client and payload == "{}") and not (payload matches '\{"id":0,"senderId":0.*"arnstep":0\}')
  ```

- **Filter Socket.IO / Engine.io Numerical Pings (`2` & `3`):**
  ```sql
  payload != "2" and payload != "3"
  ```

---

### 2. Hunting for Sensitive Credentials & Tokens
- **Find Bearer Tokens and JWTs:**
  ```sql
  payload contains "Bearer" or payload matches "eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+"
  ```

- **Find Passwords or API Keys Sent by Client:**
  ```sql
  dir == client and (payload contains "password" or payload contains "apikey" or payload contains "secret")
  ```

---

### 3. Application Security & Error Inspection
- **Information Disclosure (Stack Traces & Errors):**
  ```sql
  dir == server and (payload contains "exception" or payload contains "traceback" or payload contains "syntax error")
  ```

- **Authentication & Authorization Failures (401 / 403 equivalents):**
  ```sql
  dir == server and (payload contains "unauthorized" or payload contains "forbidden" or payload contains "access denied")
  ```

- **Isolate Repeater Messages Only:**
  ```sql
  tool == "Repeater"
  ```

- **Identify Large Payloads (Potential Exfiltration or File Transfers):**
  ```sql
  len > 2048 and dir == incoming
  ```

- **Isolate Binary Frames:**
  ```sql
  type == "Binary"
  ```

---

### 4. Multi-Target Scope Filtering
- **Focus on Specific Endpoint:**
  ```sql
  host == "chat.target.com" and path startswith "/ws"
  ```

- **Exclude Telemetry or Analytics Domains:**
  ```sql
  host !contains "analytics" and host !contains "telemetry"
  ```

---

## 7. Tips & Best Practices

1. **Live Syntax Feedback:**  
   Watch the status label next to the **Apply** button:
   - `✓ Ready` or `✓ Valid syntax`: Query is syntactically sound.
   - `✗ Error message`: Live syntax notification (e.g. unclosed quote or missing parenthesis).

2. **Quick Filter Integration:**  
   The UI checkboxes (`Outgoing`, `Incoming`, `Hide Heartbeats`, `In Scope Only`) automatically combine with your active query using `AND` logic.

3. **Instant Keyboard Navigation:**  
   Press **Enter** inside the Query Filter text field to apply your filter immediately without clicking the button.
