# AGENTS.md

This file contains the project's build, lint, test commands, and code style guidelines.
These instructions are now located in CLAUDE.md which serves as the authoritative source for all project documentation.

See CLAUDE.md for complete information on:
- Build Commands
- Lint Commands  
- Test Commands
- Running Single Tests
- Code Style Guidelines

## Repository Structure Exploration

The repository structure can be explored using the aider tool with the following command:

```bash
aider --map-tokens 1024 --show-repo-map
```

This command provides a detailed map of the repository layout, showing:
- High-level directory structure
- File organization patterns
- Project architecture overview
- Key components and their relationships

The N parameter (1024 in this example) sets how many tokens are included in the map. Higher values provide more detail but use more context:
- Lower values (e.g., 512): Quick overview of main directories and file types
- Medium values (e.g., 1024–2048): Detailed view with more specific directory structure
- Higher values (e.g., 2048–4096): Comprehensive detail including nested directories, file extensions, and project organization

Adjust based on how much structural information you want to see for your current task.