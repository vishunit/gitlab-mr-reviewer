# GitLab Merge Request Reviewer

This project is a Spring Boot application that automates the review of GitLab Merge Requests using AI. It fetches code diffs from GitLab and leverages Groq's LLM API to provide intelligent code review comments and suggestions.

## Features

- Connects to a GitLab instance using a personal access token
- Fetches merge request details and code diffs
- Integrates with Groq's LLM API for automated code review
- Supports multiple configuration profiles for secure key management

## Getting Started

1. Clone the repository.
2. Configure your sensitive keys in `src/main/resources/application-local.properties`.
3. Set the active profile to `local` (default is set in `application.properties`).
4. Run the application using Maven:

   ```
   mvn spring-boot:run
   ```

## Configuration

- All sensitive information (API keys, tokens) should be placed in `application-local.properties` and excluded from version control.
- Default configuration is in `application.properties`.

## Security

- Do not commit sensitive keys to version control.
- Use profiles to manage environment-specific settings.

## Requirements

- Java 17+
- Maven

## License

MIT License

