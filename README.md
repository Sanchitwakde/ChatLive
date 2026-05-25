# ChatApp

ChatApp is a Java socket-based chat application with a Swing client and a MySQL-backed server. It supports login, public chat, private messages, group chat, typing indicators, and message history.

## Project Structure

- `src/client` - Swing UI and client-side chat logic
- `src/server` - server, authentication, groups, and message storage
- `lib` - third-party libraries, including the MySQL JDBC driver
- `bin` - compiled `.class` files generated after build

## Requirements

- A MySQL server
- A database named `chatapp`
- A Java Development Kit
- The included MySQL connector jar in `lib/mysql-connector-j-9.5.0.jar`

## Database Setup

Create the database first:

```sql
CREATE DATABASE chatapp;
USE chatapp;
```

Create the tables used by the app:

```sql
CREATE TABLE IF NOT EXISTS users (
  id INT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(64) UNIQUE NOT NULL,
  password VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS messages (
  id INT AUTO_INCREMENT PRIMARY KEY,
  sender VARCHAR(64) NOT NULL,
  receiver VARCHAR(64) NULL,
  groupname VARCHAR(64) NULL,
  message TEXT NOT NULL,
  timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

Insert a test user:

```sql
INSERT INTO users(username, password) VALUES ('testuser', 'test123');
```

## Database Configuration

The server reads its MySQL settings from environment variables or JVM system properties.

Environment variables:

- `CHATAPP_DB_URL`
- `CHATAPP_DB_USER`
- `CHATAPP_DB_PASS`

Optional auth overrides if your table or column names are different:

- `CHATAPP_AUTH_TABLE`
- `CHATAPP_AUTH_USER_COLUMN`
- `CHATAPP_AUTH_PASSWORD_COLUMN`

Default connection values:

- URL: `jdbc:mysql://localhost:3306/chatapp?useSSL=false&serverTimezone=UTC`
- User and password are read from environment variables or JVM system properties
- Do not commit your real database credentials to the repository

## Build

From the `ChatApp` folder:

```powershell
javac -cp ".;lib/mysql-connector-j-9.5.0.jar" -d bin (Get-ChildItem -Recurse -Filter *.java src | % FullName)
```

## Run

Start the server first:

```powershell
java -cp ".;bin;lib/mysql-connector-j-9.5.0.jar" server.ChatServer
```

Then start the client in a second terminal:

```powershell
java -cp ".;bin;lib/mysql-connector-j-9.5.0.jar" client.ChatClientGUI
```

The client connects to `localhost:5000`.

## Login Notes

- The login window expects the same value that is stored in the database `username` field, or the `email` field if your table has one.
- Passwords are currently checked as plain text.
- Use environment variables or run configuration settings for database credentials instead of editing the source with real secrets.
- If login fails, check the server console for the exact reason. The server now returns clearer auth errors when the database schema or values do not match.

## Features

- Public chat
- Private messages
- Group chat
- Typing indicators
- Message history
- MySQL-backed login

## Troubleshooting

- If you get `port already in use`, another Java server is still running on port `5000`.
- If login fails immediately, verify the MySQL connection settings and confirm that the `users` table contains the exact credentials you typed.
- If your table name or column names differ, set the `CHATAPP_AUTH_*` environment variables before starting the server.
- Before pushing to GitHub, double-check that no real database password is stored in `DatabaseConnection.java` or any IDE run configuration.
